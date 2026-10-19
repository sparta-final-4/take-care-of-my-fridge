#!/bin/sh

set -e

read_env_value() {
    key="$1"
    file_path="$2"

    if [ ! -f "$file_path" ]; then
        return 1
    fi

    raw_value=$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "$file_path" | tail -n 1 | sed -E "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//")

    if [ -z "$raw_value" ]; then
        return 1
    fi

    trimmed_value=$(printf '%s' "$raw_value" | sed -E 's/[[:space:]]+$//')

    case "$trimmed_value" in
        \"*\") trimmed_value=${trimmed_value#\"}; trimmed_value=${trimmed_value%\"} ;;
        \'*\') trimmed_value=${trimmed_value#\'}; trimmed_value=${trimmed_value%\'} ;;
    esac

    printf '%s' "$trimmed_value"
}

# 생성에 쓰는 스펙 파일. 저장소에 커밋해서 백엔드 서버 없이도(CI 등) 클라이언트를 만들 수 있게 한다.
SPEC_FILE="openapi/fridge-api.json"
OUTPUT_DIR="src/common/api/generated"

# --offline: 서버에서 받지 않고 커밋된 스펙 파일로만 생성한다(CI용).
# 인자가 없으면 실행 중인 백엔드에서 스펙을 받아 SPEC_FILE을 갱신한 뒤 생성한다.
if [ "${1:-}" = "--offline" ]; then
    if [ ! -f "$SPEC_FILE" ]; then
        echo "$SPEC_FILE not found. Run 'pnpm oag:generate' with the backend running first."
        exit 1
    fi
    echo "Using committed spec $SPEC_FILE (offline)..."
else
    if [ -z "${OPENAPI_SPEC_URL:-}" ]; then
        OPENAPI_SPEC_URL="$(read_env_value OPENAPI_SPEC_URL ./.env || true)"
    fi

    if [ -z "${OPENAPI_SPEC_URL:-}" ]; then
        OPENAPI_SPEC_URL="http://localhost:8080/v3/api-docs"
    fi

    # macOS mktemp는 끝의 X만 바꾸므로 확장자가 붙은 템플릿 대신 임시 디렉터리를 쓴다.
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT
    SPEC_TMP="$TMP_DIR/spec.json"

    echo "Downloading spec from $OPENAPI_SPEC_URL..."
    curl --http1.1 -sSf \
        --connect-timeout 5 \
        --max-time 60 \
        --retry 3 \
        --retry-delay 1 \
        "$OPENAPI_SPEC_URL" -o "$SPEC_TMP"

    # servers(받아온 서버 주소)는 개발자마다 달라서 지운다. 클라이언트는 basePath를
    # 따로 받으므로 영향이 없고, 커밋되는 스펙의 diff가 API 변경만 보이게 된다.
    node -e '
        const fs = require("fs");
        const spec = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
        delete spec.servers;
        fs.writeFileSync(process.argv[2], JSON.stringify(spec, null, 2) + "\n");
    ' "$SPEC_TMP" "$SPEC_FILE"
    echo "Saved spec to $SPEC_FILE. Commit it together with API changes."
fi

# 이전 생성 결과를 지워 삭제된 API·모델이 남지 않게 한다(.openapi-generator-ignore는 유지).
find "$OUTPUT_DIR" -mindepth 1 ! -name '.openapi-generator-ignore' -exec rm -rf {} +

openapi-generator-cli generate \
    -g typescript-axios \
    -i "$SPEC_FILE" \
    -t openapi/templates \
    --skip-validate-spec \
    -o "$OUTPUT_DIR" \
    --additional-properties=withInterfaces=true,supportsES6=true,useSingleRequestParameter=true,modelPropertyNaming=original,withSeparateModelsAndApi=true,apiPackage=apis,modelPackage=models \
    --global-property=modelDocs=false,apiDocs=false,apiTests=false,modelTests=false

# 생성 코드는 tsconfig의 엄격한 규칙(noUnusedParameters 등)을 따르지 않으므로 타입 검사에서 제외한다.
# 생성 코드를 import하는 쪽의 타입 검사는 그대로 동작한다.
find "$OUTPUT_DIR" -name '*.ts' -type f | while read -r file; do
    { printf '// @ts-nocheck\n'; cat "$file"; } > "$file.tmp" && mv "$file.tmp" "$file"
done

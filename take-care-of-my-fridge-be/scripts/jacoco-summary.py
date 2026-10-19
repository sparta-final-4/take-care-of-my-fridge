import os
import xml.etree.ElementTree as ET

report = "build/reports/jacoco/test/jacocoTestReport.xml"

if not os.path.exists(report):
    print("JaCoCo XML report not found")
    raise SystemExit(0)

root = ET.parse(report).getroot()


def get_coverage(element, counter_type):
    counter = element.find(f"counter[@type='{counter_type}']")

    if counter is None:
        return None

    missed = int(counter.attrib["missed"])
    covered = int(counter.attrib["covered"])
    total = missed + covered

    if total == 0:
        return 100.0

    return covered / total * 100


def badge(value):
    if value is None:
        return "-"

    if value >= 80:
        return f"🟢 {value:.1f}%"
    elif value >= 70:
        return f"🟡 {value:.1f}%"
    else:
        return f"🔴 {value:.1f}%"


overall_line = get_coverage(root, "LINE")
overall_branch = get_coverage(root, "BRANCH")

services = []

for package in root.findall("package"):
    package_name = package.attrib["name"]

    for clazz in package.findall("class"):
        class_name = clazz.attrib["name"]
        simple_name = class_name.split("/")[-1]

        if not simple_name.endswith("Service"):
            continue

        if "$" in simple_name:
            continue

        services.append({
            "name": simple_name,
            "package": package_name.replace("/", "."),
            "line": get_coverage(clazz, "LINE"),
            "branch": get_coverage(clazz, "BRANCH"),
        })

services.sort(
    key=lambda x: x["line"] if x["line"] is not None else 100
)

#########

lines = []

lines.append("# 📊 JaCoCo Coverage Report\n")
lines.append("## Overall Coverage\n")
lines.append("| Metric | Coverage |")
lines.append("|---|---:|")
lines.append(f"| Lines | **{badge(overall_line)}** |")
lines.append(f"| Branches | **{badge(overall_branch)}** |\n")

lines.append("## Service Coverage\n")

if not services:
    lines.append("_Service classes not found._")
else:
    lines.append("| Service | Line | Branch |")
    lines.append("|---|---:|---:|")

    for service in services:
        lines.append(
            f"| `{service['name']}` "
            f"| {badge(service['line'])} "
            f"| {badge(service['branch'])} |"
        )

content = "\n".join(lines) + "\n"

# GitHub Actions Summary (로컬 실행 시에는 없으므로 건너뛴다)
summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
if summary_path:
    with open(summary_path, "a") as f:
        f.write(content)

# PR Comment 용 파일
with open("jacoco-comment.md", "w") as f:
    f.write(content)

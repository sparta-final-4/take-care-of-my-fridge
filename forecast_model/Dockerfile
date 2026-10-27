FROM python:3.12-slim
WORKDIR /app
COPY forecast_model/requirements.txt forecast_model/requirements.txt
RUN pip install --no-cache-dir -r forecast_model/requirements.txt \
    --index-url https://download.pytorch.org/whl/cpu
COPY forecast_model forecast_model
ENV HOST=0.0.0.0 PORT=8091 FORECAST_MODEL_DIR=/models
EXPOSE 8091
CMD ["python", "-m", "forecast_model.worker"]

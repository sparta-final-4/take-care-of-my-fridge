"""Internal HTTP worker for training and weekly inference."""

import json
import os
from datetime import date
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import torch

from forecast_model.artifact import MODEL_VERSION, save_model
from forecast_model.inference import predict
from forecast_model.model import WeeklySalesModel
from forecast_model.training import train


def run_forecast(payload, model_dir=None):
    samples = payload["samples"]
    products = payload["products"]
    if not samples or not products:
        raise ValueError("real training samples and products are required")
    week_start = date.fromisoformat(payload["weekStart"]).isoformat()
    torch.manual_seed(7)
    torch.set_num_threads(1)
    model = train(WeeklySalesModel(),
                  [sample["history"] for sample in samples],
                  [sample["target"] for sample in samples])
    predictions = [{"productId": item["productId"],
                    "quantity": predict(model, item["history"])}
                   for item in products]
    if model_dir is not None:
        target = Path(model_dir) / (week_start + ".pt")
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(".tmp")
        save_model(model, temporary)
        temporary.replace(target)
    return {"modelVersion": MODEL_VERSION, "predictions": predictions}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/predict":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 10_000_000:
                raise ValueError("invalid payload size")
            payload = json.loads(self.rfile.read(length))
            result = run_forecast(payload, os.environ.get("FORECAST_MODEL_DIR"))
            body = json.dumps(result).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (KeyError, TypeError, ValueError) as exc:
            self.send_error(400, str(exc))


if __name__ == "__main__":
    HTTPServer((os.environ.get("HOST", "127.0.0.1"),
                int(os.environ.get("PORT", "8091"))), Handler).serve_forever()

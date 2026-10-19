"""Persist the trained weekly sales model and its version."""

import torch

from forecast_model.model import WINDOW_DAYS, WeeklySalesModel

MODEL_VERSION = "weekly-mlp-v1"


def save_model(model, path):
    torch.save({
        "version": MODEL_VERSION,
        "window_days": WINDOW_DAYS,
        "weights": model.state_dict(),
    }, path)


def load_model(path):
    artifact = torch.load(path, map_location="cpu", weights_only=True)
    if artifact["version"] != MODEL_VERSION or artifact["window_days"] != WINDOW_DAYS:
        raise ValueError("unsupported forecast model artifact")
    model = WeeklySalesModel()
    model.load_state_dict(artifact["weights"])
    model.eval()
    return model

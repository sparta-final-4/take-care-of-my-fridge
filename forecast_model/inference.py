"""Predict the next seven-day total from one product's last 28 days."""

import torch

from forecast_model.model import WINDOW_DAYS
from forecast_model.training import _normalize


def predict(model, history):
    history = torch.as_tensor(history, dtype=torch.float32)
    if history.shape != (WINDOW_DAYS,):
        raise ValueError(f"history must contain {WINDOW_DAYS} daily quantities")
    normalized, scale = _normalize(history.unsqueeze(0))
    model.eval()
    with torch.no_grad():
        quantity = model(normalized)[0] * scale[0]
    return max(0, round(quantity.item()))

"""Train and use the weekly sales neural network."""

import torch
from torch import nn

from forecast_model.model import WINDOW_DAYS


def _normalize(histories):
    if histories.ndim != 2 or histories.shape[0] == 0 or histories.shape[1] != WINDOW_DAYS:
        raise ValueError(f"histories must have {WINDOW_DAYS} daily quantities per row")
    if not torch.isfinite(histories).all() or (histories < 0).any():
        raise ValueError("daily quantities must be finite and nonnegative")
    scale = histories.mean(dim=1).clamp(min=1.0)
    return histories / scale.unsqueeze(1), scale


def train(model, histories, targets):
    histories = torch.as_tensor(histories, dtype=torch.float32)
    targets = torch.as_tensor(targets, dtype=torch.float32)
    normalized, scale = _normalize(histories)
    if targets.shape != scale.shape or not torch.isfinite(targets).all() or (targets < 0).any():
        raise ValueError("targets must contain one nonnegative quantity per row")

    optimizer = torch.optim.Adam(model.parameters(), lr=0.01)
    model.train()
    for _ in range(200):
        optimizer.zero_grad()
        loss = nn.functional.mse_loss(model(normalized), targets / scale)
        loss.backward()
        optimizer.step()
    return model

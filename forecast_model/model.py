"""Neural network for next-week product sales prediction."""

from torch import nn

WINDOW_DAYS = 28


class WeeklySalesModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.layers = nn.Sequential(
            nn.Linear(WINDOW_DAYS, 32),
            nn.ReLU(),
            nn.Linear(32, 16),
            nn.ReLU(),
            nn.Linear(16, 1),
            nn.Softplus(),
        )

    def forward(self, normalized_history):
        return self.layers(normalized_history).squeeze(-1)

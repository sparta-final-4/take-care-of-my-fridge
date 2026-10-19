import unittest

import torch

from forecast_model.model import WINDOW_DAYS, WeeklySalesModel
from forecast_model.training import _normalize, train


class WeeklySalesTrainingTest(unittest.TestCase):
    def setUp(self):
        torch.manual_seed(7)
        torch.set_num_threads(1)

    def test_training_reduces_error_on_synthetic_sales(self):
        histories = torch.tensor([
            [base + slope * day + day % 7 for day in range(WINDOW_DAYS)]
            for base in (5, 10, 20, 40)
            for slope in (0, 0.1, 0.3)
        ], dtype=torch.float32)
        targets = torch.tensor([
            sum(base + slope * day + day % 7 for day in range(28, 35))
            for base in (5, 10, 20, 40)
            for slope in (0, 0.1, 0.3)
        ], dtype=torch.float32)
        model = WeeklySalesModel()
        normalized, scale = _normalize(histories)
        before = torch.nn.functional.mse_loss(model(normalized), targets / scale).item()

        train(model, histories, targets)

        after = torch.nn.functional.mse_loss(model(normalized), targets / scale).item()
        self.assertLess(after, before)

    def test_rejects_empty_training_data(self):
        with self.assertRaises(ValueError):
            train(WeeklySalesModel(), [], [])


if __name__ == "__main__":
    unittest.main()

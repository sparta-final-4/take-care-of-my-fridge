import unittest

import torch

from forecast_model.model import WINDOW_DAYS, WeeklySalesModel


class WeeklySalesModelTest(unittest.TestCase):
    def test_returns_nonnegative_forecast_for_each_product(self):
        model = WeeklySalesModel()
        histories = torch.ones((2, WINDOW_DAYS))

        forecasts = model(histories)

        self.assertEqual(forecasts.shape, (2,))
        self.assertTrue(torch.all(forecasts >= 0).item())


if __name__ == "__main__":
    unittest.main()

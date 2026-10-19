import tempfile
import unittest
from pathlib import Path

import torch

from forecast_model.artifact import load_model, save_model
from forecast_model.model import WeeklySalesModel


class ForecastArtifactTest(unittest.TestCase):
    def test_saved_model_produces_same_output(self):
        torch.manual_seed(7)
        model = WeeklySalesModel()
        history = torch.ones((1, 28))
        expected = model(history).item()

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "forecast.pt"
            save_model(model, path)
            loaded = load_model(path)
            actual = loaded(history).item()

        self.assertAlmostEqual(actual, expected)


if __name__ == "__main__":
    unittest.main()

import tempfile
import unittest
from pathlib import Path

from forecast_model.artifact import load_model
from forecast_model.worker import run_forecast


class ForecastWorkerTest(unittest.TestCase):
    def test_trains_predicts_and_saves_real_run_artifact(self):
        payload = {
            "weekStart": "2026-10-26",
            "samples": [{"history": [2] * 28, "target": 14}],
            "products": [{"productId": 1, "history": [3] * 28}],
        }
        with tempfile.TemporaryDirectory() as directory:
            result = run_forecast(payload, directory)
            artifact = Path(directory) / "2026-10-26.pt"

            self.assertEqual(result["modelVersion"], "weekly-mlp-v1")
            self.assertEqual(result["predictions"][0]["productId"], 1)
            self.assertGreaterEqual(result["predictions"][0]["quantity"], 0)
            self.assertTrue(artifact.is_file())
            self.assertIsNotNone(load_model(artifact))


if __name__ == "__main__":
    unittest.main()

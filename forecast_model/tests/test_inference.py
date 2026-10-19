import unittest

import torch
from torch import nn

from forecast_model.inference import predict


class ConstantModel(nn.Module):
    def forward(self, histories):
        return torch.full((histories.shape[0],), 7.0)


class WeeklySalesInferenceTest(unittest.TestCase):
    def test_restores_sales_scale(self):
        self.assertEqual(predict(ConstantModel(), [10] * 28), 70)

    def test_rejects_invalid_history(self):
        with self.assertRaises(ValueError):
            predict(ConstantModel(), [1] * 27)
        with self.assertRaises(ValueError):
            predict(ConstantModel(), [-1] + [1] * 27)


if __name__ == "__main__":
    unittest.main()

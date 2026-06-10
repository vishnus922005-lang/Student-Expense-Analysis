import joblib
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import StringTensorType
import onnx

model = joblib.load("expense_char_model.pkl")

initial_type = [("input", StringTensorType([None, 1]))]

onnx_model = convert_sklearn(
    model,
    initial_types=initial_type,
    options={id(model): {"zipmap": False}}
)

with open("expense_char_model.onnx", "wb") as f:
    f.write(onnx_model.SerializeToString())

onnx.checker.check_model("expense_char_model.onnx")
print("Saved + Verified: expense_char_model.onnx ✅")

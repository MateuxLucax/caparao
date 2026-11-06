import importlib.machinery
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/capture/capture-scenarios.py"
LOADER = importlib.machinery.SourceFileLoader("capture_scenarios", str(SCRIPT))
SPEC = importlib.util.spec_from_loader(LOADER.name, LOADER)
capture_scenarios = importlib.util.module_from_spec(SPEC)
LOADER.exec_module(capture_scenarios)


class CaptureScenarioTests(unittest.TestCase):
    def test_all_scenarios_have_ordered_named_checkpoints(self):
        paths = sorted(capture_scenarios.SCENARIO_DIR.glob("*.json"))
        self.assertGreaterEqual(len(paths), 6)
        for path in paths:
            plan = capture_scenarios.scenario_plan(path)
            self.assertTrue(plan, path.name)
            self.assertEqual(sorted(plan), plan, path.name)
            self.assertEqual(len({name for _, name in plan}), len(plan), path.name)

    def test_espresso_capture_waits_for_named_dose_and_keeps_portafilter(self):
        source = SCRIPT.read_text()
        self.assertIn('tap_text("Free brew")', source)
        self.assertIn('wait_for_text("Weigh the beans")', source)
        self.assertIn('wait_for_text("Prepare the portafilter")', source)
        self.assertIn('wait_for_text("Start the shot")', source)

    def test_filter_capture_selects_the_built_in_recipe(self):
        source = SCRIPT.read_text()
        self.assertIn('tap_text("Melitta 102")', source)
        self.assertIn('wait_for_text("1:15", contains=True)', source)

    def test_capture_starts_from_home_instead_of_a_restored_brew(self):
        source = SCRIPT.read_text()
        self.assertIn('"am", "start", "-S"', source)
        self.assertIn('"0x10008000"', source)

    def test_filter_capture_waits_for_its_preparation_step(self):
        source = SCRIPT.read_text()
        self.assertIn('wait_for_text("Prepare the filter")', source)
        self.assertIn('wait_for_bottom("Done")', source)

    def test_french_press_capture_waits_for_its_preparation_step(self):
        source = SCRIPT.read_text()
        self.assertIn('wait_for_text("Prepare the press")', source)
        self.assertIn('tap_text("Ready")', source)
        self.assertIn('wait_for_bottom("Done")', source)

    def test_french_press_plan_captures_steep_and_press_and_serve(self):
        plan = capture_scenarios.scenario_plan(capture_scenarios.SCENARIO_DIR / "french-press.json")
        names = [name for _, name in plan]
        self.assertIn("steep", names)
        self.assertIn("press-and-serve", names)

    def test_filter_plan_captures_lift_and_saved_summary(self):
        plan = capture_scenarios.scenario_plan(capture_scenarios.SCENARIO_DIR / "filter.json")
        names = [name for _, name in plan]
        self.assertIn("lifted", names)
        self.assertIn("summary", names)

    def test_capture_requires_the_configured_emulator(self):
        with self.assertRaisesRegex(ValueError, "emulator"):
            capture_scenarios.require_emulator("adb", "", Mock())

        runner = Mock(return_value=type("Result", (), {"stdout": "List of devices attached\nemulator-5554 device\n"})())
        capture_scenarios.require_emulator("adb", "emulator-5554", runner)
        runner.assert_called_once_with(["adb", "devices"], check=True, text=True, capture_output=True)

    def test_capture_rejects_a_physical_device(self):
        with self.assertRaisesRegex(ValueError, "only runs on an emulator"):
            capture_scenarios.require_emulator("adb", "device-serial", Mock())


if __name__ == "__main__":
    unittest.main()

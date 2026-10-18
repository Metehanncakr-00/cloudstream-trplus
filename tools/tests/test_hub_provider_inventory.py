import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class HubProviderInventoryTests(unittest.TestCase):
    def test_enabled_federation_modules_match_packaged_and_runtime_inventory(self):
        registry = json.loads((ROOT / "config/providers.json").read_text(encoding="utf-8"))
        enabled = {
            provider["module"] for provider in registry["providers"]
            if provider["enabled"] and provider["module"] not in {"CloudStreamHub", "YTS", "FourKHDHub"}
        }
        build = (ROOT / "CloudStreamHub/build.gradle.kts").read_text(encoding="utf-8")
        bundle_block = re.search(r"val bundledProviderModules = listOf\((.*?)\)", build, re.S)
        self.assertIsNotNone(bundle_block)
        packaged = re.findall(r'"([^"\n]+)"', bundle_block.group(1))
        self.assertEqual(len(packaged), len(set(packaged)), "Bundle modules must not repeat")
        self.assertEqual(enabled, set(packaged))

        adapter = (ROOT / "CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/"
                   "CloudStreamProviderRegistryAdapter.kt").read_text(encoding="utf-8")
        runtime_block = re.search(r"val knownProviderClasses = listOf\((.*?)\)", adapter, re.S)
        self.assertIsNotNone(runtime_block)
        classes = re.findall(r'"([^"\n]+)"', runtime_block.group(1))
        self.assertEqual(len(classes), len(set(classes)), "Runtime classes must not repeat")
        runtime_modules = {name.rsplit(".", 1)[1] for name in classes}
        self.assertEqual(enabled, runtime_modules)
        for name in classes:
            module = name.rsplit(".", 1)[1]
            source = ROOT / module / "src/main/kotlin" / Path(*name.split(".")).with_suffix(".kt")
            self.assertTrue(source.is_file(), f"Runtime class has no packaged source: {name}")


if __name__ == "__main__":
    unittest.main()

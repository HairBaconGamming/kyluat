import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from enforcer.bootstrap import load_main

main = load_main(ROOT)

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or ["run"]))

import sys

try:
    from .cli import main
except ImportError as exc:
    from .bootstrap import emit, explain

    emit(explain(exc))
    sys.exit(1)

if __name__ == "__main__":
    sys.exit(main())

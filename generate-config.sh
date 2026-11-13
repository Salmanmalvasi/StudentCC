#!/bin/bash

# Build script to generate google-services.json from environment variables
# Works with a local .env file or CI-provided environment variables

set -euo pipefail

mock_mode=false
require_production=false
build_release=false
for argument in "$@"; do
    case "$argument" in
        --mock) mock_mode=true ;;
        --require-production) require_production=true ;;
        --build) build_release=true; require_production=true ;;
        *) echo "Unknown option: $argument" >&2; exit 1 ;;
    esac
done

if [ "$mock_mode" = true ] && [ "$require_production" = true ]; then
    echo "Mock Firebase configuration cannot be used for a release build." >&2
    exit 1
fi

echo "🔐 Generating google-services.json from environment variables..."

if [ "$mock_mode" = false ] && [ -f ".env" ]; then
    source .env
fi

required_vars=("FIREBASE_API_KEY" "FIREBASE_PROJECT_ID" "FIREBASE_PROJECT_NUMBER" "FIREBASE_APP_ID" "FIREBASE_STORAGE_BUCKET")

has_missing=false
for var in "${required_vars[@]}"; do
    if [ -z "${!var:-}" ]; then
        has_missing=true
        break
    fi
done

if [ "$require_production" = true ] && [ "$has_missing" = true ]; then
    echo "Production Firebase configuration requires all FIREBASE_* environment variables." >&2
    exit 1
fi

if [ "$mock_mode" = true ] || [ "$has_missing" = true ]; then
    if [ "$mock_mode" = false ] && [ -s "app/google-services.json" ]; then
        echo "⚠️ Warning: Firebase environment variables not fully set, but app/google-services.json exists. Using existing configuration."
        exit 0
    else
        echo "⚠️ Warning: Firebase environment variables missing. Generating mock configuration for build verification."
        FIREBASE_PROJECT_NUMBER="000000000000"
        FIREBASE_PROJECT_ID="studentcc-mock"
        FIREBASE_STORAGE_BUCKET="studentcc-mock.appspot.com"
        FIREBASE_APP_ID="1:000000000000:android:0000000000000000"
        FIREBASE_API_KEY="AIzaSyMockKeyForBuildVerification0000"
    fi
fi

echo "📝 Generating app/google-services.json..."

export "${required_vars[@]}"
export STUDENTCC_REQUIRE_PRODUCTION="$require_production"
python3 - <<'PY'
import json, os, pathlib, re
names = ('FIREBASE_PROJECT_NUMBER', 'FIREBASE_PROJECT_ID', 'FIREBASE_STORAGE_BUCKET', 'FIREBASE_APP_ID', 'FIREBASE_API_KEY')
values = {name: os.environ[name] for name in names}
if os.environ['STUDENTCC_REQUIRE_PRODUCTION'] == 'true':
    placeholders = [name for name, value in values.items()
                    if re.search(r'(?i)(your[_ -]|placeholder|mock|example)', value)]
    if values['FIREBASE_PROJECT_NUMBER'].strip('0') == '':
        placeholders.append('FIREBASE_PROJECT_NUMBER')
    if placeholders:
        raise SystemExit('Replace placeholder production Firebase values: ' + ', '.join(sorted(set(placeholders))))

def substitute(value):
    if isinstance(value, str):
        for name, replacement in values.items():
            value = value.replace('${' + name + '}', replacement)
        return value
    if isinstance(value, list):
        return [substitute(item) for item in value]
    if isinstance(value, dict):
        return {key: substitute(item) for key, item in value.items()}
    return value

template = pathlib.Path('app/google-services.json.template')
configuration = substitute(json.loads(template.read_text()))
pathlib.Path('app/google-services.json').write_text(json.dumps(configuration, indent=2) + '\n')
PY

echo "✅ google-services.json generated successfully!"

if [ "$build_release" = true ]; then
    echo "🚀 Building release APK..."
    ./gradlew assembleRelease
fi

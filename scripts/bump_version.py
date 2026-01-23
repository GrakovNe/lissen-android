import sys
import re
import os

def bump_version(current_version, bump_type):
    major, minor, patch = map(int, current_version.split('.'))
    if bump_type == 'major':
        major += 1
        minor = 0
        patch = 0
    elif bump_type == 'minor':
        minor += 1
        patch = 0
    elif bump_type == 'patch':
        patch += 1
    return f"{major}.{minor}.{patch}"

def update_gradle_properties(new_version):
    path = 'gradle.properties'
    with open(path, 'r') as f:
        content = f.read()

    # Update appVersionName
    content = re.sub(r'appVersionName=.*', f'appVersionName={new_version}', content)
    
    # Update appVersionCode (increment by 1)
    version_code_match = re.search(r'appVersionCode=(\d+)', content)
    if version_code_match:
        old_code = int(version_code_match.group(1))
        new_code = old_code + 1
        content = re.sub(r'appVersionCode=\d+', f'appVersionCode={new_code}', content)

    with open(path, 'w') as f:
        f.write(content)

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python bump_version.py <current_version> <bump_type>")
        sys.exit(1)
    
    current_version = sys.argv[1]
    bump_type = sys.argv[2]
    
    new_version = bump_version(current_version, bump_type)
    update_gradle_properties(new_version)
    print(new_version)

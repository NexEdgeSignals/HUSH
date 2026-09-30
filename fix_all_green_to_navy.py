import os
import re

PROJECT_ROOT = r"C:\Projects\GitHub2\SilenceNG"

# Navy Blue Targets
NAVY_MAIN = "1A3A8F"   # Primary Navy
NAVY_DARK = "132A67"   # Status Bar / Dark Navy
NAVY_LIGHT = "6B8BDB"  # Light Accent

print("🔍 Scanning all project files (.xml and .java) for green colors...")

def classify_and_replace_hex(match, is_java=False):
    full_match = match.group(0)
    hex_val = match.group(1)
    
    # Standardize to R, G, B
    if len(hex_val) == 8: # AARRGGBB
        alpha = hex_val[:2]
        r = int(hex_val[2:4], 16)
        g = int(hex_val[4:6], 16)
        b = int(hex_val[6:8], 16)
    elif len(hex_val) == 6: # RRGGBB
        alpha = ""
        r = int(hex_val[:2], 16)
        g = int(hex_val[2:4], 16)
        b = int(hex_val[4:6], 16)
    else:
        return full_match

    # Check if green is the dominant/strongest color component
    if g > r and g > b and g > 50 and (g - max(r, b)) > 10:
        # Determine shade
        if g < 110:
            new_rgb = NAVY_DARK
        elif g > 200:
            new_rgb = NAVY_LIGHT
        else:
            new_rgb = NAVY_MAIN
            
        if is_java:
            return f"0x{alpha if alpha else 'FF'}{new_rgb}"
        else:
            return f"#{alpha}{new_rgb}"
            
    return full_match

modified_files = []

for root, dirs, files in os.walk(PROJECT_ROOT):
    if "build" in root or ".gradle" in root or ".git" in root:
        continue
        
    for file in files:
        if file.endswith(".xml") or file.endswith(".java"):
            file_path = os.path.join(root, file)
            
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
                
            # Regex 1: XML hex colors (#RRGGBB or #AARRGGBB)
            new_content = re.sub(r'#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})\b', lambda m: classify_and_replace_hex(m, is_java=False), content)
            
            # Regex 2: Java hex colors (0xFFRRGGBB or 0xAARRGGBB)
            new_content = re.sub(r'0x([0-9a-fA-F]{8})\b', lambda m: classify_and_replace_hex(m, is_java=True), new_content)
            
            if new_content != content:
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write(new_content)
                modified_files.append(file_path)

print(f"\n✅ Successfully updated green colors to Navy Blue in {len(modified_files)} files:")
for f in modified_files:
    print(f" - {f}")

print("\n🎉 Sweep complete! All green elements updated.")
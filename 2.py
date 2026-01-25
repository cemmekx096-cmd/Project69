import os

# Konfigurasi GitHub
username = "cemmekx096-cmd"
repo = "project69"
branch = "main"

output_file = "links_github.txt"
counter = 1

print("📁 Scanning .github folder...")

# Cek apakah folder .github ada
if not os.path.exists(".github"):
    print("❌ Folder .github tidak ditemukan!")
    exit()

print("✅ Folder .github ditemukan, memproses...")

with open(output_file, "w", encoding="utf-8") as f:
    f.write("=" * 50 + "\n")
    f.write("FOLDER: .github\n")
    f.write("=" * 50 + "\n\n")
    
    for root, dirs, files in os.walk(".github"):
        # Urutkan file
        files.sort()
        
        for file in files:
            filepath = os.path.join(root, file)
            filepath = filepath.replace("\\", "/")
            
            # Generate URL
            url = f"https://raw.githubusercontent.com/{username}/{repo}/{branch}/{filepath}"
            
            # Tulis ke file
            f.write(f"{counter}. {file}\n")
            f.write(f"   Path: {filepath}\n")
            f.write(f"   URL: {url}\n\n")
            
            counter += 1
            print(f"   ✓ {filepath}")

print(f"\n{'='*50}")
print(f"✅ BERHASIL!")
print(f"{'='*50}")
print(f"📊 Total files: {counter-1}")
print(f"📄 Output file: {output_file}")
import os
from pathlib import Path
import sys
from datetime import datetime

def generate_tree_structure(directory, output_file="tree_structure.txt", 
                           ignore_dirs=None, include_hidden=False):
    """
    Generate folder/file structure dan langsung simpan ke .txt file
    
    Args:
        directory: Path direktori yang akan discan
        output_file: Nama file output (default: tree_structure.txt)
        ignore_dirs: Direktori yang diabaikan
        include_hidden: Include hidden files/folders (dimulai dengan .)
    
    Returns:
        Path ke file output
    """
    if ignore_dirs is None:
        ignore_dirs = ['.git', '__pycache__', '.idea', '.vscode', 
                      'node_modules', 'venv', '.venv', 'env', '.env']
    
    if not output_file.endswith('.txt'):
        output_file += '.txt'
    
    path = Path(directory)
    output_path = Path(output_file)
    
    # Header informasi
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    with open(output_path, 'w', encoding='utf-8') as f:
        # Write header
        f.write(f"Struktur Folder/File\n")
        f.write(f"Dicetak pada: {timestamp}\n")
        f.write(f"Direktori: {path.absolute()}\n")
        f.write("=" * 60 + "\n\n")
        
        def write_tree(current_path, prefix="", level=0):
            """Helper function untuk write tree secara rekursif"""
            try:
                items = list(current_path.iterdir())
            except PermissionError:
                f.write(f"{prefix}[Permission Denied]\n")
                return
            
            # Filter items
            filtered_items = []
            for item in items:
                # Skip jika di ignore_dirs
                if item.name in ignore_dirs:
                    continue
                # Skip hidden files jika tidak diinclude
                if not include_hidden and item.name.startswith('.'):
                    continue
                filtered_items.append(item)
            
            # Sort: folders first, then files, alphabetically
            filtered_items.sort(key=lambda x: (not x.is_dir(), x.name.lower()))
            
            for i, item in enumerate(filtered_items):
                is_last = i == len(filtered_items) - 1
                
                if is_last:
                    connector = "└── "
                    next_prefix = prefix + "    "
                else:
                    connector = "├── "
                    next_prefix = prefix + "│   "
                
                # Tulis item
                item_display = item.name
                if item.is_dir():
                    item_display += "/"
                
                f.write(f"{prefix}{connector}{item_display}\n")
                
                # Jika folder, rekursif
                if item.is_dir():
                    write_tree(item, next_prefix, level + 1)
        
        # Mulai dari root
        write_tree(path)
        
        # Footer
        f.write("\n" + "=" * 60 + "\n")
        f.write(f"Total file yang di-scan: {count_files(path, ignore_dirs, include_hidden)}\n")
        f.write(f"File output: {output_path.absolute()}\n")
    
    print(f"✅ Struktur berhasil di-generate ke: {output_path.absolute()}")
    print(f"   Gunakan perintah: cat {output_file} atau type {output_file}")
    
    return output_path

def count_files(directory, ignore_dirs, include_hidden):
    """Hitung total file (non-direktori) dalam struktur"""
    count = 0
    path = Path(directory)
    
    for item in path.rglob("*"):
        # Skip jika di ignore_dirs
        if any(ignore in str(item) for ignore in ignore_dirs):
            continue
        
        # Skip hidden jika tidak diinclude
        if not include_hidden and item.name.startswith('.'):
            continue
        
        if item.is_file():
            count += 1
    
    return count

def print_sample_output():
    """Cetak contoh output ke terminal untuk preview"""
    sample_dir = "."
    print("\n" + "="*60)
    print("PREVIEW OUTPUT:")
    print("="*60)
    
    path = Path(sample_dir)
    items = sorted([item for item in path.iterdir() 
                   if not item.name.startswith('.') and item.name not in ['.git', '__pycache__']],
                  key=lambda x: (not x.is_dir(), x.name.lower()))
    
    for i, item in enumerate(items[:5]):  # Hanya tampilkan 5 item pertama sebagai preview
        is_last = i == len(items) - 1 or i == 4
        connector = "└── " if is_last else "├── "
        
        item_display = item.name
        if item.is_dir():
            item_display += "/"
        
        print(f"{connector}{item_display}")
        
        # Jika folder, tampilkan 2 item dalamnya
        if item.is_dir() and i < 3:
            try:
                sub_items = list(item.iterdir())[:2]
                for j, sub_item in enumerate(sub_items):
                    sub_connector = "└── " if j == len(sub_items) - 1 else "├── "
                    print(f"    {sub_connector}{sub_item.name}")
            except:
                pass
    
    print("\n" + "="*60)
    print("File lengkap akan disimpan di .txt\n")

def main():
    """Fungsi utama dengan argumen command line"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Generate struktur folder/file ke file .txt',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Contoh penggunaan:
  python bikinkan.py                    # Generate dari folder saat ini
  python bikinkan.py /path/to/folder    # Generate dari folder tertentu
  python bikinkan.py --output struktur  # Custom nama file
  python bikinkan.py --hidden           # Include hidden files
  python bikinkan.py --depth 3          # Batasi kedalaman
        """
    )
    
    parser.add_argument('directory', nargs='?', default='.', 
                       help='Direktori target (default: .)')
    parser.add_argument('--output', '-o', default='tree_structure',
                       help='Nama file output tanpa .txt (default: tree_structure)')
    parser.add_argument('--hidden', '-H', action='store_true',
                       help='Include hidden files/folders')
    parser.add_argument('--ignore', '-i', nargs='+',
                       default=['.git', '__pycache__', '.idea', '.vscode', 'node_modules'],
                       help='List folder/file yang diabaikan')
    parser.add_argument('--preview', '-p', action='store_true',
                       help='Preview di terminal sebelum generate')
    
    args = parser.parse_args()
    
    # Tampilkan preview jika diminta
    if args.preview:
        print_sample_output()
    
    # Generate tree structure
    output_file = generate_tree_structure(
        directory=args.directory,
        output_file=args.output,
        ignore_dirs=args.ignore,
        include_hidden=args.hidden
    )
    
    # Tampilkan isi file jika kecil
    file_size = output_file.stat().st_size
    if file_size < 50000:  # Jika file < 50KB
        print("\n" + "="*60)
        print("ISI FILE OUTPUT:")
        print("="*60)
        with open(output_file, 'r', encoding='utf-8') as f:
            print(f.read())

if __name__ == "__main__":
    main()
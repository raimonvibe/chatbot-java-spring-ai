"""
Generate Bible verse embeddings using Cohere API in Google Colab
This script is free to run in Colab and can be faster than generating in Java
Currently configured to process New Testament only

Usage in Google Colab:
1. Upload this script and the New Testament JSON file to Colab
2. Install dependencies: !pip install cohere pandas tqdm
3. Set your COHERE_API_KEY
4. Run the script
5. Download the generated embeddings JSON file
6. Import into database using the Java import script
"""

import json
import os
import time
from typing import List, Dict
import cohere
from tqdm import tqdm
import pandas as pd

# Configuration
COHERE_API_KEY = os.getenv('COHERE_API_KEY', 'your-api-key-here')
BATCH_SIZE = 100  # Cohere allows up to 96 texts per request
MODEL = "embed-multilingual-v3.0"  # Same model as Java app
OUTPUT_FILE = "bible_embeddings.json"

# Paths to Bible data files (upload these to Colab)
OLD_TESTAMENT_PATH = "old-testament-data.json"
NEW_TESTAMENT_PATH = "new-testament-data.json"

def parse_verse_from_chapter_content(book_name: str, chapter_number: int, content: str) -> List[Dict]:
    """Parse individual verses from chapter content"""
    import re
    verse_pattern = re.compile(r'\[\s*(\d+)\s*\]\s*([^\[]+)')
    verses = []
    
    for match in verse_pattern.finditer(content):
        verse_number = int(match.group(1))
        verse_text = match.group(2).strip()
        
        if verse_text:
            verses.append({
                'book': book_name,
                'chapter': chapter_number,
                'verse': verse_number,
                'reference': f"{book_name} {chapter_number}:{verse_number}",
                'text': verse_text,
                'translation': 'World English Bible'
            })
    
    return verses

def load_bible_data() -> List[Dict]:
    """Load all Bible verses from JSON files (New Testament only)"""
    all_verses = []
    
    # Only load New Testament
    file_path = NEW_TESTAMENT_PATH
    if not os.path.exists(file_path):
        print(f"⚠️  Warning: {file_path} not found. Please upload it to Colab.")
        return all_verses
        
    print(f"📖 Loading {file_path} (New Testament only)...")
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    books = data.get('books', [])
    for book in books:
        book_name = book.get('name')
        chapters = book.get('chapters', [])
        
        for chapter in chapters:
            chapter_number = int(chapter.get('number'))
            content = chapter.get('content', '')
            verses = parse_verse_from_chapter_content(book_name, chapter_number, content)
            all_verses.extend(verses)
    
    print(f"✅ Loaded {len(all_verses)} verses from {file_path}")
    
    return all_verses

def generate_embeddings_batch(co: cohere.Client, texts: List[str]) -> List[List[float]]:
    """Generate embeddings for a batch of texts"""
    try:
        response = co.embed(
            texts=texts,
            model=MODEL,
            input_type="search_document"
        )
        return response.embeddings
    except Exception as e:
        print(f"❌ Error generating embeddings: {e}")
        # Retry logic
        time.sleep(2)
        try:
            response = co.embed(
                texts=texts,
                model=MODEL,
                input_type="search_document"
            )
            return response.embeddings
        except Exception as e2:
            print(f"❌ Retry failed: {e2}")
            return None

def generate_all_embeddings(verses: List[Dict], co: cohere.Client) -> List[Dict]:
    """Generate embeddings for all verses"""
    print(f"\n🚀 Starting embedding generation for {len(verses)} verses...")
    print(f"📊 Using model: {MODEL}")
    print(f"📦 Batch size: {BATCH_SIZE}")
    print(f"💰 Estimated API calls: {len(verses) // BATCH_SIZE + 1}\n")
    
    results = []
    
    # Process in batches
    for i in tqdm(range(0, len(verses), BATCH_SIZE), desc="Generating embeddings"):
        batch = verses[i:i + BATCH_SIZE]
        texts = [verse['text'] for verse in batch]
        
        # Generate embeddings
        embeddings = generate_embeddings_batch(co, texts)
        
        if embeddings is None:
            print(f"⚠️  Skipping batch {i // BATCH_SIZE + 1}")
            continue
        
        # Add embeddings to verses
        for verse, embedding in zip(batch, embeddings):
            verse['embedding'] = embedding
            results.append(verse)
        
        # Rate limiting - be nice to the API
        time.sleep(0.5)  # Small delay between batches
    
    return results

def save_embeddings(verses_with_embeddings: List[Dict], output_file: str):
    """Save embeddings to JSON file"""
    print(f"\n💾 Saving embeddings to {output_file}...")
    
    # Convert embeddings to the format expected by Java
    output_data = {
        'version': '1.0',
        'model': MODEL,
        'total_verses': len(verses_with_embeddings),
        'verses': verses_with_embeddings
    }
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    file_size = os.path.getsize(output_file) / (1024 * 1024)  # MB
    print(f"✅ Saved {len(verses_with_embeddings)} verses with embeddings")
    print(f"📁 File size: {file_size:.2f} MB")

def main():
    print("=" * 60)
    print("📖 Bible Verse Embedding Generator for Google Colab")
    print("=" * 60)
    
    # Check API key
    if COHERE_API_KEY == 'your-api-key-here' or not COHERE_API_KEY:
        print("❌ Error: COHERE_API_KEY not set!")
        print("   Set it with: os.environ['COHERE_API_KEY'] = 'your-key'")
        return
    
    # Initialize Cohere client
    co = cohere.Client(COHERE_API_KEY)
    
    # Load Bible data
    verses = load_bible_data()
    if not verses:
        print("❌ No verses loaded. Please check file paths.")
        return
    
    print(f"\n📊 Total verses to process: {len(verses)}")
    
    # Generate embeddings
    verses_with_embeddings = generate_all_embeddings(verses, co)
    
    if not verses_with_embeddings:
        print("❌ No embeddings generated!")
        return
    
    # Save results
    save_embeddings(verses_with_embeddings, OUTPUT_FILE)
    
    print("\n" + "=" * 60)
    print("✅ Embedding generation complete!")
    print("=" * 60)
    print(f"\n📥 Next steps:")
    print(f"1. Download {OUTPUT_FILE} from Colab")
    print(f"2. Use the Java import script to load embeddings into database")
    print(f"3. Run: java -jar import-embeddings.jar {OUTPUT_FILE}")

if __name__ == "__main__":
    main()


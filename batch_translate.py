import os
import xml.etree.ElementTree as ET
import urllib.parse
import urllib.request
import json
import time

def translate_batch(texts, target_lang):
    # Google Translate has ~5000 chars limit. Our total string length is likely smaller, but let's join them with a delimiter.
    # Newlines work best.
    combined = "\n\n".join(texts)
    
    url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=" + target_lang + "&dt=t"
    data = urllib.parse.urlencode({'q': combined}).encode('utf-8')
    
    try:
        req = urllib.request.Request(url, data=data, headers={'User-Agent': 'Mozilla/5.0'})
        response = urllib.request.urlopen(req)
        result = json.loads(response.read().decode('utf-8'))
        
        translated_full = "".join([ret[0] for ret in result[0] if ret[0]])
        # Split by \n\n
        translated_texts = [x.strip() for x in translated_full.split("\n\n")]
        
        # If length mismatches, we fallback to English
        if len(translated_texts) == len(texts):
            return translated_texts
        else:
            print("Length mismatch, falling back to English")
            return texts
    except Exception as e:
        print(f"Error {e}, falling back to English")
        return texts

languages = ["ca", "cs", "de", "es", "fr", "hr", "id", "hu", "nl", "pl", "pt", "ro", "fi", "sv", "vi", "tr", "el", "bg", "ru", "sr", "uk", "ja", "zh"]

tree = ET.parse("app/src/main/res/values/strings.xml")
root = tree.getroot()

keys = []
texts = []
for child in root:
    if child.tag == "string" and child.text and child.attrib["name"] != "app_name":
        keys.append(child.attrib["name"])
        texts.append(child.text)

app_name_idx = None
for child in root:
    if child.tag == "string" and child.attrib["name"] == "app_name":
         app_name_val = child.text

for lang in languages:
    print(f"Translating to {lang}...")
    target_dir = f"app/src/main/res/values-{lang}"
    os.makedirs(target_dir, exist_ok=True)
    
    translated_texts = translate_batch(texts, lang)
    
    target_xml = f"{target_dir}/strings.xml"
    
    with open(target_xml, "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('<resources>\n')
        f.write(f'    <string name="app_name">{app_name_val}</string>\n')
        for i in range(len(keys)):
            trans = translated_texts[i]
            # primitive escape
            trans = trans.replace("'", "\\'").replace('"', '\\"').replace("&", "&amp;")
            f.write(f'    <string name="{keys[i]}">{trans}</string>\n')
        f.write('</resources>\n')
        
    time.sleep(1) # Be nice to the API

print("Done")

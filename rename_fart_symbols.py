import os
import re

# 敏感方法名及其替代名映射表
RENAME_MAP = {
    "getClassField": "resolveDeclaredField",
    "getClassFieldObject": "extractFieldValue",
    "invokeStaticMethod": "invokeStaticByName",
    "getFieldOjbect": "getInstanceFieldValue",
    "getClassloader": "obtainAppClassLoader",
    "loadClassAndInvoke": "dispatchClassTask",
    "fart\\b": "startCodeInspection",
    "fartwithClassloader": "startCodeInspectionWithCL",
    "fartthread": "launchInspectorThread",
    "dumpMethodCode": "nativeDumpCode",
    "codeitem_end": "getDexCodeItemEnd",
    "base64_encode": "encodeBase64Buffer",
    "dumpDexFileByExecute": "traceDexExecution",
    "dumpArtMethod": "traceMethodCode",
    "myfartInvoke": "callNativeMethodInspector",
    "DexFile_dumpMethodCode": "DexFile_nativeDumpCode",
    "jobject2ArtMethod": "convertToArtMethodPtr"
}

SOURCE_SUFFIX = (".java", ".kt", ".cc", ".c", ".cpp", ".h", ".js")

def replace_in_file(file_path):
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()

        original_content = content

        for old, new in RENAME_MAP.items():
            content = re.sub(r'\b' + old + r'\b', new, content)

        if content != original_content:
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"[UPDATED] {file_path}")
        else:
            print(f"[SKIPPED] {file_path}")

    except Exception as e:
        print(f"[ERROR] {file_path}: {e}")

def scan_directory(root_dir):
    for dirpath, _, filenames in os.walk(root_dir):
        for file in filenames:
            if file.endswith(SOURCE_SUFFIX):
                replace_in_file(os.path.join(dirpath, file))

if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Usage: python rename_fart_symbols.py <source_directory>")
        sys.exit(1)

    scan_directory(sys.argv[1])

    input("Press Enter to exit...")

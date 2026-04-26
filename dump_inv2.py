import sqlite3
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

conn = sqlite3.connect(r'c:\Users\teamr\Desktop\miencraft\shared-data\smp.db')
cur = conn.cursor()

# Check snapshots: 2544 (last quit before spam), 2537 (periodic)
for snap_id in [2544, 2537]:
    cur.execute("SELECT id, name, source, datetime(created_at/1000, 'unixepoch') as time, yaml FROM inv_snapshots WHERE id = ?", (snap_id,))
    row = cur.fetchone()
    print(f"\n=== Snapshot ID {row[0]} | {row[1]} | {row[2]} | {row[3]} ===")
    print(row[4])
    print("=== END ===")

conn.close()

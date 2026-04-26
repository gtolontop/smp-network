import sqlite3

conn = sqlite3.connect(r'c:\Users\teamr\Desktop\miencraft\shared-data\smp.db')
cur = conn.cursor()

cur.execute(
    "SELECT id, name, source, server, datetime(created_at/1000, 'unixepoch') as time "
    "FROM inv_snapshots WHERE name = 'harry_warior' ORDER BY created_at DESC LIMIT 20"
)
rows = cur.fetchall()
print(f"Nombre de snapshots: {len(rows)}")
for r in rows:
    print(r)

conn.close()

"""Convert test-only synthetic fixture JSON and the project's XML mappings to explicit SQL.
Run FinalDemoFixtures first. Never reads a live database or local secrets.
"""
from pathlib import Path
import json, re, xml.etree.ElementTree as ET
rows=json.loads(Path('target/demo-fixtures.json').read_text(encoding='utf-8'))
ns={'m':'https://jakarta.ee/xml/ns/persistence/orm'}
mapping={}
for path in Path('src/main/resources/META-INF').glob('*orm.xml'):
    for e in ET.parse(path).getroot().findall('m:entity',ns):
        table=e.find('m:table',ns).get('name'); cols=[]
        for a in e.find('m:attributes',ns):
            field=a.get('name'); c=a.find('m:column',ns)
            if a.tag.endswith('many-to-one'): c=a.find('m:join-column',ns)
            if a.tag.endswith('transient'):continue
            cols.append((field,c.get('name') if c is not None and c.get('name') else field,a.tag.endswith('many-to-one')))
        mapping[table]=(e.get('class'),cols)
def literal(v):
    if v is None:return 'NULL'
    if isinstance(v,bool):return 'TRUE' if v else 'FALSE'
    if isinstance(v,(int,float)):return str(v)
    return "'"+str(v).replace("'","''")+"'"
sql=["-- SYNTHETIC DEMO ONLY. Accounts: admin, buyer, seller, reader, reviewer, manager.","-- Password for demo accounts: demo-password-123. Never use these accounts in production.","BEGIN;", "DO $$ BEGIN IF current_database() <> 'goc_sach_final_demo' THEN RAISE EXCEPTION 'Dedicated demo database required'; END IF; IF EXISTS(SELECT 1 FROM users) THEN RAISE EXCEPTION 'Refusing to overwrite existing data'; END IF; END $$;"]
for table in re.findall(r'CREATE TABLE (\w+)',Path('sql/schema.sql').read_text(encoding='utf-8')):
    if table not in mapping:continue
    cls,cols=mapping[table];records=rows.get(cls,[])
    for r in records:
        values=[]
        for field,col,relationship in cols:
            v=r.get(field)
            if relationship and v is not None:v=v['id']
            values.append('DEFAULT' if field=='id' and v is None else literal(v))
        sql.append(f"INSERT INTO {table} ({','.join(c for f,c,rel in cols)}) VALUES ({','.join(values)});")
    if records and any(f=='id' for f,c,rel in cols):sql.append(f"SELECT setval(pg_get_serial_sequence('{table}','id'),(SELECT MAX(id) FROM {table}),true);")
sql.append('COMMIT;')
Path('sql/demo-final.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
print('Wrote synthetic fixture SQL',len(sql),'statements')

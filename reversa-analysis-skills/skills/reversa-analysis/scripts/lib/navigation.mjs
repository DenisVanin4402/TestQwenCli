import fs from 'node:fs';
import path from 'node:path';
import {json,rootAt,safePath} from './io.mjs';
import {slug,withoutGenerated} from './markdown.mjs';
const label=t=>String(t).replace(/[\[\]<>\r\n]/g,' ');
export function navigate(directory,{write=false}={}){
 const root=rootAt(directory),cat=json(safePath(root,'catalog.json'));
 if(!Array.isArray(cat.documents)||!cat.documents.length)throw new Error('Пустой каталог');
 const map=new Map(cat.documents.map(d=>[d.id,d]));if(map.size!==cat.documents.length)throw new Error('Повторные ID');
 const entry=cat.entrypoint||'README.md';if(!cat.documents.some(d=>d.path===entry))throw new Error('Входная страница не зарегистрирована');
 const planned=[],destinations=new Set();
 for(const d of cat.documents){
  const dest=safePath(root,d.path);if(destinations.has(dest.toLowerCase()))throw new Error('Повторный путь');destinations.add(dest.toLowerCase());
  const original=fs.readFileSync(dest,'utf8');
  // Ambiguous markers must not swallow authored content.
  for(const tag of ['nav','children'])for(const edge of ['start','end'])if((original.match(new RegExp('<!-- rsa:'+tag+':'+edge+' -->','g'))||[]).length>1)throw new Error('Повторные маркеры: '+d.path);
  for(const tag of ['nav','children'])if(original.includes('<!-- rsa:'+tag+':start -->')!==original.includes('<!-- rsa:'+tag+':end -->'))throw new Error('Непарные маркеры: '+d.path);
  const ranges=['nav','children'].map(tag=>({start:original.indexOf('<!-- rsa:'+tag+':start -->'),end:original.indexOf('<!-- rsa:'+tag+':end -->')})).filter(r=>r.start>=0).sort((a,b)=>a.start-b.start);
  if(ranges.some(r=>r.start>=r.end)||ranges.some((r,i)=>i&&ranges[i-1].end>r.start))throw new Error('Перепутанные или вложенные маркеры: '+d.path);
  let body=withoutGenerated(original);const toc=[],used=new Map();let fence=null;
  body=body.split(/\r?\n/).map(line=>{
   const f=line.match(/^\s*(`{3,}|~{3,})(.*)$/);if(f){if(!fence)fence={char:f[1][0],len:f[1].length};else if(f[1][0]===fence.char&&f[1].length>=fence.len&&!f[2].trim())fence=null;return line;}
   const h=!fence&&line.match(/^(#{2,3}) (.+)$/);if(!h)return line;
   const base='rsa-'+slug(h[2]),n=used.get(base)||0,id=base+(n?'-'+n:'');used.set(base,n+1);
   toc.push((h[1].length===3?'  ':'')+`- [${label(h[2])}](#${id})`);return `<a id="${id}"></a>\n${line}`;
  }).join('\n');
  const href=target=>path.posix.relative(path.posix.dirname(d.path),target)||path.posix.basename(target);
  const chain=[],seen=new Set([d.id]);let id=d.parent;
  while(id!=null){if(seen.has(id)||!map.has(id))throw new Error('Неверная иерархия: '+d.id);seen.add(id);const p=map.get(id);chain.unshift(`[${label(p.title)}](${href(p.path)})`);id=p.parent;}
  const nav=`<!-- rsa:nav:start -->\n[Содержание](${href(entry)})${chain.length?' · '+chain.join(' → '):''}\n\n**На этой странице**\n\n${toc.join('\n')}\n<!-- rsa:nav:end -->\n\n`;
  const h1=body.match(/^# .+\n(?:\n)?/);if(!h1)throw new Error('Нужен H1 в начале: '+d.path);body=h1[0]+nav+body.slice(h1[0].length).replace(/^\n+/,'');
  const children=[];
  const append=(parent,depth)=>{for(const child of cat.documents.filter(c=>c.parent===parent)){children.push('  '.repeat(depth)+`- [${child.id} · ${label(child.title)}](${href(child.path)})`);if(d.path===entry)append(child.id,depth+1);}};
  if(d.path===entry){for(const parent of cat.documents.filter(c=>c.parent==null)){if(parent.id===d.id)append(parent.id,0);else{children.push(`- [${parent.id} · ${label(parent.title)}](${href(parent.path)})`);append(parent.id,1);}}}else append(d.id,0);
  body=body.trimEnd()+`\n\n<!-- rsa:children:start -->\n${children.length?'**Документы раздела**\n\n'+children.join('\n')+'\n\n':''}[К содержанию](${href(entry)})\n<!-- rsa:children:end -->\n`;
  if(body!==original)planned.push({path:d.path,dest,body});
 }
 // Complete preflight before the first mutation.
 if(write)for(const p of planned)fs.writeFileSync(p.dest,p.body);
 return {mode:write?'written':'preview',documents:cat.documents.length,changed:planned.map(p=>p.path)};
}

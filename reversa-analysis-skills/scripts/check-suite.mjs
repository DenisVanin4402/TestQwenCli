import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';
import {walk,json,safePath} from '../skills/reversa-analysis/scripts/lib/io.mjs';
import {links} from '../skills/reversa-analysis/scripts/lib/markdown.mjs';
import {validate} from '../skills/reversa-analysis/scripts/lib/validate.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),suite=json(path.join(root,'suite.json')),errors=[];
let count=0,templates=0;
for(const name of suite.skills){
 const file=path.join(root,'skills',name,'SKILL.md'),body=fs.readFileSync(file,'utf8'),match=body.match(/^---\nname: ([a-z0-9-]+)\ndescription: ("[^\n]+")\n---\n/);
 if(!match||match[1]!==name)errors.push('Метаданные '+name);else{const desc=JSON.parse(match[2]);if(!desc||desc.length>1024||name.length>64||name.includes('--'))errors.push('Формат '+name);}
 if(body.includes('disable-model-invocation')||body.includes('$ARGUMENTS')||body.includes('allowed-tools:'))errors.push('Платформенное расширение в общем ядре '+name);
 count++;
}
for(const name of walk(root)){
 if(name.endsWith('.json'))try{json(path.join(root,name));}catch(e){errors.push(name+': '+e.message);}
 if(name.includes('/assets/templates/')&&name.endsWith('.md'))templates++;
 if(!name.endsWith('.md'))continue;
 const body=fs.readFileSync(path.join(root,name),'utf8');
 for(const href of links(body)){
  if(/^(?:https?:|mailto:|#)/.test(href)||href.includes('{{'))continue;
  const local=href.split('#')[0];if(!local)continue;
  try{const file=safePath(root,path.posix.join(path.posix.dirname(name),decodeURIComponent(local)));if(!fs.statSync(file).isFile())throw new Error('не файл');}catch(e){errors.push(name+' → '+href+' — '+e.message);}
 }
}
const example=validate(path.join(root,'examples/library-lending'),{strict:true});if(!example.ok)errors.push(...example.errors.map(e=>e.detail));
console.log(JSON.stringify({ok:!errors.length,skills:count,templates,exampleDocuments:example.documents,errors,note:'Проверка общего подмножества метаданных и ресурсов; запуск конкретного агента и Mermaid-рендер проверяются отдельно.'},null,2));process.exitCode=errors.length?1:0;

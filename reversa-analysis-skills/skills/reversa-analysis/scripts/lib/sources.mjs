import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {json,rootAt,safePath,sha256} from './io.mjs';
export function inspect(project){
 const root=rootAt(project),warnings=[],roots=['.reversa','_reversa_sdd'];
 const state=safePath(root,'.reversa/state.json');
 if(fs.existsSync(state))try{const s=json(state);if(s.output_folder){safePath(root,s.output_folder);roots.push(s.output_folder);}}catch(e){warnings.push('state.json: '+e.message);}
 let baseline=null;try{baseline=execFileSync('git',['-C',root,'rev-parse','HEAD'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}).trim();}catch{warnings.push('Версия Git не установлена; задайте версию источников явно.');}
 const files=[],seen=new Set();
 function visit(relative){
  let file;try{file=safePath(root,relative);}catch(e){warnings.push(e.message);return;}
  if(!fs.existsSync(file))return;const st=fs.lstatSync(file);if(st.isSymbolicLink()){warnings.push('Пропущена ссылка: '+relative);return;}
  if(st.isDirectory()){
   if(['analysis-config.json','catalog.json','evidence.json'].every(n=>fs.existsSync(path.join(file,n)))){warnings.push('Пропущен уже собранный пакет аналитики: '+relative);return;}
   for(const name of fs.readdirSync(file).sort())if(!['.git','node_modules','_reversa_analysis','_analysis'].includes(name))visit(relative+'/'+name);return;
  }
  if(!/\.(md|json|ya?ml)$/i.test(relative)||/(?:^|\/)(?:\.env|credentials|secrets)(?:\.|\/|$)/i.test(relative)||seen.has(file))return;
  seen.add(file);files.push({path:relative,bytes:st.size,sha256:sha256(fs.readFileSync(file))});
 }
 for(const r of new Set(roots))visit(r);
 return {project:root,baseline,files,warnings,note:'Инвентаризация материалов; наличие файла не подтверждает завершённость анализа. Исходный код здесь не сканируется.'};
}
export function impact(directory,{sourceRoot}={}){
 const root=rootAt(directory),ev=json(safePath(root,'evidence.json')),cat=json(safePath(root,'catalog.json'));
 if(!Array.isArray(ev.sources)||!Array.isArray(ev.claims)||!Array.isArray(cat.documents))throw new Error('Неверный реестр');
 const changes=[];
 for(const s of ev.sources){
  if(!s||typeof s.locator!=='string')throw new Error('Неверный источник');
  if(/^https?:/.test(s.locator)){changes.push({id:s.id,state:'remote_unchecked'});continue;}
  let file;try{file=path.isAbsolute(s.locator)?s.locator:safePath(sourceRoot?rootAt(sourceRoot):root,s.locator);if(!fs.statSync(file).isFile())throw new Error('Нет файла');}catch{changes.push({id:s.id,state:'unavailable'});continue;}
  const current=sha256(fs.readFileSync(file));changes.push({id:s.id,state:s.sha256?(current===s.sha256?'unchanged':'changed'):'unbaselined',currentSha256:current,recordedSha256:s.sha256||null});
 }
 const changedIds=new Set(changes.filter(x=>x.state!=='unchanged').map(x=>x.id));
 const affectedClaims=ev.claims.filter(c=>Array.isArray(c.sourceIds)&&c.sourceIds.some(id=>changedIds.has(id)));
 const docs=new Set(affectedClaims.map(c=>c.documentId)),direct=[...docs];
 const tracePath=safePath(root,'traceability.json');let trace=[];if(fs.existsSync(tracePath))trace=json(tracePath).requirements||[];
 const affectedRequirements=new Set();let changed=true;
 while(changed){changed=false;for(const r of trace){const refs=[r.documentId,...(r.specificationIds||[]),...(r.ruleRefs||[]).map(x=>x.documentId),...(r.acceptanceRefs||[]).map(x=>x.documentId)];if(refs.some(id=>docs.has(id))){affectedRequirements.add(r.id);for(const id of refs)if(!docs.has(id)){docs.add(id);changed=true;}}}}
 return {sources:changes,claims:affectedClaims.map(c=>c.id),directDocuments:direct,reviewDocuments:cat.documents.filter(d=>docs.has(d.id)).map(d=>({id:d.id,path:d.path,status:d.status})),requirements:[...affectedRequirements],note:'Кандидаты для проверки. Изменение байтов не доказывает изменение поведения; источник, baseline, тексты и согласования не перезаписаны.'};
}

import fs from 'node:fs';
import path from 'node:path';
import {json,rootAt,safePath} from './io.mjs';
import {anchors,diagrams,links,prose} from './markdown.mjs';
const types=json(new URL('../../assets/document-types.json',import.meta.url));
const text=x=>typeof x==='string'&&x.trim().length>0;
const states=['draft','in_review','approved','superseded'];
const object=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
export function validate(directory,{strict=false}={}){
 const errors=[],warnings=[];const bad=(code,detail)=>errors.push({code,detail});const warn=(code,detail)=>warnings.push({code,detail});
 let cat={},ev={},root,blocks=0,traceRequirements=0;
 try{
  root=rootAt(directory);cat=json(safePath(root,'catalog.json'));ev=json(safePath(root,'evidence.json'));
  if(!object(cat)||!object(ev))throw new Error('Реестры должны быть объектами');
 }catch(e){return {ok:false,errors:[{code:'READ',detail:e.message}],warnings};}
 const list=(x,label)=>{if(!Array.isArray(x)){bad('ARRAY',label);return [];}if(x.some(i=>!object(i)))bad('RECORD',label);return x.filter(object);};
 const docs=list(cat.documents,'documents'),sources=list(ev.sources,'sources'),claims=list(ev.claims,'claims'),questions=list(ev.questions,'questions');
 if(cat.version!==1||!text(cat.system)||!text(cat.baseline)||!docs.length)bad('CATALOG','Нужны version=1, system, baseline, documents');
 const ids=new Set();for(const item of [...docs,...sources,...claims,...questions]){if(!/^[A-Z]+-\d{3,}$/.test(item.id||''))bad('ID',String(item.id));if(ids.has(item.id))bad('DUPLICATE_ID',item.id);ids.add(item.id);}
 const map=new Map(docs.map(d=>[d.id,d])),sourceMap=new Map(sources.map(s=>[s.id,s])),questionMap=new Map(questions.map(q=>[q.id,q]));
 const bodies=new Map(),seenPaths=new Set();
 const approved=item=>{if(item.status==='approved'&&(!text(item.approvedBy)||!text(item.approvedAt)||Number.isNaN(Date.parse(item.approvedAt))))bad('APPROVAL',item.id+' — отсутствует основание согласования');};
 const idArray=(value,map,label,{required=true}={})=>{if(!Array.isArray(value)){if(required||value!=null)bad('REFERENCES',label);return [];}if(value.some(id=>!map.has(id)))bad('REFERENCES',label);return value;};
 for(const d of docs){
  if(!text(d.title)||!types[d.kind]||!states.includes(d.status))bad('DOCUMENT',d.id);
  if(d.audience!=null&&!['reader','evidence'].includes(d.audience))bad('AUDIENCE',d.id);
  approved(d);const seen=new Set([d.id]);let p=d.parent;
  while(p!=null){if(!map.has(p)){bad('PARENT',d.id+' → '+p);break;}if(seen.has(p)){bad('CYCLE',d.id);break;}seen.add(p);p=map.get(p).parent;}
  try{
   if(!text(d.path)||!d.path.endsWith('.md'))throw new Error('Нужен Markdown-файл');
   const file=safePath(root,d.path);if(seenPaths.has(file.toLowerCase()))bad('DUPLICATE_PATH',d.path);seenPaths.add(file.toLowerCase());
   const body=fs.readFileSync(file,'utf8');bodies.set(d.id,body);const content=prose(body);
   if(/\{\{[^}]+\}\}|\[TODO:[^\]]+\]/.test(body))bad('PLACEHOLDER',d.path);
   if(!/^# .+/m.test(content))bad('TITLE',d.path);
   for(const section of types[d.kind]||[])if(!content.split(/\r?\n/).includes('## '+section))bad('SECTION',d.id+': '+section);
   const explicit=[...content.matchAll(/\b(?:id|name)=["']([^"']+)["']/g)].map(m=>m[1]);if(new Set(explicit).size!==explicit.length)bad('DUPLICATE_ANCHOR',d.path);
   blocks+=diagrams(body).length;
   for(const block of diagrams(body))if(!block.trim())bad('EMPTY_DIAGRAM',d.path);
   if(d.audience!=='evidence'&&/(?:backend|frontend)\/(?:src|lib)|\.(?:java|dart|py|tsx?):\d+/.test(content))warn('IMPLEMENTATION_DETAIL',d.id);
   for(const href of links(body)){
    if(/^(https?:|mailto:)/i.test(href))continue;
    if(d.audience==='evidence'&&/^(?:[a-zA-Z]:|file:)/.test(href))continue;
    try{
     if(href.startsWith('MISSING_REFERENCE:'))throw new Error('Не определена ссылочная метка');
     const decoded=decodeURIComponent(href),hash=decoded.indexOf('#'),target=hash<0?decoded:decoded.slice(0,hash),anchor=hash<0?'':decoded.slice(hash+1);
     const relative=target?path.posix.join(path.posix.dirname(d.path),target):d.path;
     // Reject absolute/protocol targets before path.join could reinterpret them.
     if(target&&(/^(?:[a-zA-Z][\w+.-]*:|\/|\\)/.test(target)))throw new Error('Внешний локальный путь');
     const dest=safePath(root,relative);if(!fs.statSync(dest).isFile())throw new Error('Ссылка должна вести к файлу');
     if(anchor&&!anchors(fs.readFileSync(dest,'utf8')).has(anchor))throw new Error('Нет якоря '+anchor);
    }catch(e){bad('LINK',d.id+': '+href+' — '+e.message);}
   }
  }catch(e){bad('DOCUMENT_FILE',d.id+': '+e.message);}
  const own=claims.filter(c=>c.documentId===d.id);if(!own.length&&d.audience!=='evidence')bad('NO_CLAIMS',d.id);
  if(d.status==='approved'&&own.some(c=>c.status!=='approved'||c.basis==='unknown'))bad('UNREVIEWED_CLAIMS',d.id);
 }
 for(const s of sources){if(!['code','reversa','decision','test','documentation','synthetic'].includes(s.type)||!text(s.locator)||!text(s.revision))bad('SOURCE',s.id);if(s.sha256!=null&&!/^[a-f0-9]{64}$/i.test(s.sha256))bad('HASH',s.id);}
 for(const q of questions){
  if(!text(q.text)||!['critical','major','minor'].includes(q.severity)||!['open','answered','accepted_gap'].includes(q.status))bad('QUESTION',q.id);
  const refs=idArray(q.documentIds,map,q.id);if(!refs.length)bad('QUESTION_DOCUMENT',q.id);
  if(q.status==='answered'&&!text(q.answer))bad('ANSWER',q.id);
  if(q.status==='accepted_gap'&&!text(q.acceptance))bad('GAP_ACCEPTANCE',q.id);
  if(q.status==='open'&&q.severity==='critical'&&refs.some(id=>map.get(id)?.status==='approved'))bad('CRITICAL_QUESTION',q.id);
 }
 for(const c of claims){
  if(!map.has(c.documentId)||!text(c.text)||!['observed','required','proposed'].includes(c.kind)||!['direct','inferred','unknown'].includes(c.basis)||!states.includes(c.status))bad('CLAIM',c.id);
  approved(c);const refs=idArray(c.sourceIds,sourceMap,c.id+' sourceIds');
  if(c.basis!=='unknown'&&!refs.length)bad('MISSING_EVIDENCE',c.id);
  if(c.basis==='inferred'&&!text(c.rationale))bad('INFERENCE',c.id);
  const qs=idArray(c.questionIds,questionMap,c.id+' questionIds',{required:false});
  if(c.basis==='unknown'&&!qs.length)bad('MISSING_QUESTION',c.id);
  if(c.basis==='unknown'&&qs.some(id=>!questionMap.get(id)?.documentIds?.includes(c.documentId)))bad('QUESTION_SCOPE',c.id);
 }
 if(cat.entrypoint!=null&&!docs.some(d=>d.path===cat.entrypoint))bad('ENTRYPOINT','Страница не зарегистрирована');
 if(cat.primaryFormat!=null&&cat.primaryFormat!=='markdown')bad('FORMAT','Ожидается markdown');
 const attachmentPaths=new Set();
 for(const a of cat.attachments==null?[]:list(cat.attachments,'attachments')){
  try{if(!text(a.purpose))throw new Error('Нет назначения');const dest=safePath(root,a.path);if(!fs.statSync(dest).isFile())throw new Error('Нет файла');if(attachmentPaths.has(a.path)||docs.some(d=>d.path===a.path))throw new Error('Повторное вложение');attachmentPaths.add(a.path);}catch(e){bad('ATTACHMENT',String(a.path)+': '+e.message);}
 }
 const traceFile=safePath(root,'traceability.json');
 if(fs.existsSync(traceFile)){
  try{
   const trace=json(traceFile);if(!object(trace)||trace.version!==1)throw new Error('Нужен version=1');const requirements=list(trace.requirements,'requirements');traceRequirements=requirements.length;const seen=new Set();
   const ref=(r,label)=>{if(!object(r)||!map.has(r.documentId)||!text(r.anchor)||!anchors(bodies.get(r.documentId)||'').has(r.anchor))bad('TRACE_ANCHOR',label);};
   for(const r of requirements){
    if(!/^FR-\d{3,}$/.test(r.id||'')||seen.has(r.id))bad('TRACE_ID',String(r.id));seen.add(r.id);ref(r,r.id);
    if(!['as-is','to-be','proposal'].includes(r.mode))bad('TRACE_MODE',r.id);
    const specs=idArray(r.specificationIds,map,r.id+' specificationIds');if(!specs.length)warn('TRACE_SPEC',r.id);
    for(const key of ['ruleRefs','acceptanceRefs']){if(!Array.isArray(r[key]))bad('TRACE_REFS',r.id+' '+key);else{r[key].forEach(x=>ref(x,r.id+' '+key));if(!r[key].length)warn('TRACE_COVERAGE',r.id+' '+key);}}
    idArray(r.questionIds,questionMap,r.id+' questionIds',{required:false});
   }
  }catch(e){bad('TRACE_FILE',e.message);}
 }
 return {ok:errors.length===0&&(!strict||warnings.length===0),documents:docs.length,sources:sources.length,claims:claims.length,openQuestions:questions.filter(q=>q.status==='open').length,traceRequirements,mermaid:{blocks,syntax:'not_checked'},errors,warnings,scope:'Структура и ссылки; без доказательства предметного смысла, подлинности согласований и исполнения системы.'};
}

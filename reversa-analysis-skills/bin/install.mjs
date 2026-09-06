#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {args,only,json,rootAt,safePath,sha256,walk,writeJson,write} from '../skills/reversa-analysis/scripts/lib/io.mjs';
const suiteRoot=fileURLToPath(new URL('../',import.meta.url));
const destinations={codex:'.agents/skills',claude:'.claude/skills',qwen:'.qwen/skills',generic:'.agents/skills'};
export function install(project,{agent='generic',apply=false,update=false}={}){
 if(!destinations[agent]&&agent!=='all')throw new Error('agent: codex | claude | qwen | generic | all');
 const root=rootAt(project),suite=json(path.join(suiteRoot,'suite.json')),sourceRoot=rootAt(path.join(suiteRoot,'skills'));
 const selected=agent==='all'?['codex','claude','qwen']:[agent],plans=[],conflicts=[];
 for(const host of selected){
  const base=destinations[host],manifestRel=base+'/.reversa-analysis-install.json',manifestPath=safePath(root,manifestRel);
  const old=fs.existsSync(manifestPath)?json(manifestPath):{package:suite.name,files:{}};
  if(old.package!==suite.name||!old.files||typeof old.files!=='object'||Array.isArray(old.files))throw new Error('Чужой или повреждённый реестр установки: '+manifestRel);
  const files=[],hashes={...old.files};
  for(const name of suite.skills){
   if(!/^reversa-analysis(?:-[a-z]+)?$/.test(name))throw new Error('Неожиданное имя навыка');
   for(const file of walk(sourceRoot,name)){
    const data=fs.readFileSync(safePath(sourceRoot,file)),hash=sha256(data),relative=base+'/'+file,dest=safePath(root,relative);
    if(fs.existsSync(dest)){
     const current=sha256(fs.readFileSync(dest));
     if(current===hash){files.push({path:relative,action:'unchanged'});hashes[file]=hash;continue;}
     if(!update||old.files[file]!==current){conflicts.push({path:relative,reason:old.files[file]?'Локальная правка или не указан --update':'Существующий файл не принадлежит этому установщику'});continue;}
    }
    files.push({path:relative,action:fs.existsSync(dest)?'update':'create',data});hashes[file]=hash;
   }
  }
  plans.push({host,base,manifestRel,hashes,files});
 }
 if(apply&&conflicts.length)throw new Error('Установка не выполнена; конфликты: '+conflicts.map(c=>c.path).join(', '));
 if(apply)for(const plan of plans){for(const f of plan.files)if(f.data)write(root,f.path,f.data);writeJson(root,plan.manifestRel,{package:suite.name,version:suite.version,files:plan.hashes});}
 return {mode:apply?'applied':'preview',version:suite.version,skills:suite.skills.length,targets:plans.map(p=>({agent:p.host,path:p.base,create:p.files.filter(f=>f.action==='create').length,update:p.files.filter(f=>f.action==='update').length,unchanged:p.files.filter(f=>f.action==='unchanged').length})),conflicts,note:'Копируются все навыки рядом с общим ядром. Общие инструкции, настройки агента и Reversa не изменяются. Устаревшие файлы не удаляются.'};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))try{
 const a=args(process.argv.slice(2),['apply','update','help']);only(a,['project','agent','apply','update','help']);
 if(a.help)console.log('node bin/install.mjs --project PATH --agent codex|claude|qwen|generic|all [--apply] [--update]\nБез --apply: предварительный просмотр. --update заменяет только неизменённые файлы предыдущей установки.');
 else{if(!a.project||a._.length)throw new Error('Укажите --project PATH');const result=install(a.project,{agent:a.agent,apply:!!a.apply,update:!!a.update});console.log(JSON.stringify(result,null,2));if(result.conflicts.length)process.exitCode=1;}
}catch(e){console.error(JSON.stringify({ok:false,error:e.message}));process.exitCode=1;}

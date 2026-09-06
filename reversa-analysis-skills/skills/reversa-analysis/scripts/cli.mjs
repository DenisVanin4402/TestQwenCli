#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import {args,only,rootAt,write,writeJson} from './lib/io.mjs';
import {navigate} from './lib/navigation.mjs';
import {validate} from './lib/validate.mjs';
import {inspect,impact} from './lib/sources.mjs';

function init(directory,system,baseline){
 if(!system?.trim())throw new Error('Укажите --system');
 const dest=path.resolve(directory);
 if(fs.existsSync(dest)&&fs.readdirSync(dest).length)throw new Error('Инициализация требует пустой или новой папки: '+dest);
 fs.mkdirSync(dest,{recursive:true});const root=rootAt(dest);
 const clean=value=>String(value).replace(/[\r\n<>\[\]]/g,' ');
 const docs=[
  {id:'PAGE-001',title:'Аналитика '+clean(system),path:'README.md',kind:'overview',parent:null,status:'draft'},
  {id:'PAGE-002',title:'Границы исследования',path:'scope.md',kind:'overview',parent:'PAGE-001',status:'draft'},
  {id:'PAGE-003',title:'Открытые вопросы',path:'questions.md',kind:'questions',parent:'PAGE-001',status:'draft'},
  {id:'PAGE-004',title:'Основания анализа',path:'evidence.md',kind:'overview',parent:'PAGE-001',status:'draft',audience:'evidence'}
 ];
 const bodies=[
  '## Назначение\n\nРабочая база системной аналитики. Сейчас подготовлена структура; поведение системы ещё не извлечено.\n\n## Область описания\n\nГраница не установлена. См. Q-001. Не присваивайте этому состоянию готовность полной спецификации.\n\n## Навигация\n\n[Границы](scope.md) · [Вопросы](questions.md) · [Доказательства](evidence.md)',
  '## Назначение\n\nЗафиксировать включённые возможности и сценарии перед подробным описанием.\n\n## Область описания\n\nНе установлена — Q-001. Укажите источники, исходную версию, исключения и режим AS-IS / целевые требования.\n\n## Навигация\n\n[Вопросы](questions.md)',
  '## Назначение\n\nСохранить неизвестности без выдуманных правил.\n\n## Открытые вопросы\n\n| ID | Вопрос | Влияние |\n|---|---|---|\n| <a id="q-001"></a>Q-001 | Каковы границы и источники исследования? | Пока нельзя утверждать полноту описания |\n\n## Уже принятые решения\n\nРешения ещё не перенесены. Их отсутствие в новом пакете не отменяет решений в доступных материалах.',
  '## Назначение\n\nХранить происхождение утверждений отдельно от предметного текста.\n\n## Область описания\n\nИсточники пока не зарегистрированы. Заполните evidence.json по реально использованным материалам.\n\n## Навигация\n\n[Реестр](evidence.json)'
 ];
 docs.forEach((d,i)=>write(root,d.path,`# ${d.title}\n\n> ${d.id} · Черновик, содержание не извлечено\n\n${bodies[i]}\n`));
 writeJson(root,'catalog.json',{version:1,system,baseline:baseline||'unrecorded',scope:'Не установлена; создана только структура',primaryFormat:'markdown',entrypoint:'README.md',documents:docs,attachments:[]});
 writeJson(root,'evidence.json',{sources:[],claims:docs.slice(0,3).map((d,i)=>({id:'CLM-'+String(i+1).padStart(3,'0'),documentId:d.id,text:'Граница и источники исследования ещё не установлены',kind:'proposed',basis:'unknown',sourceIds:[],questionIds:['Q-001'],status:'draft'})),questions:[{id:'Q-001',documentIds:docs.slice(0,3).map(d=>d.id),text:'Каковы границы и источники исследования?',severity:'major',status:'open'}]});
 writeJson(root,'traceability.json',{version:1,requirements:[]});
 writeJson(root,'analysis-config.json',{version:1,language:'ru',mode:'as-is',sourceRoots:[],capabilityScope:[],outputFormat:'markdown',state:'initialized'});
 navigate(root,{write:true});return {output:root,status:'initialized',note:'Создана структура; извлечение знаний выполняет агент.'};
}

const help=`Reversa Analysis — локальные помощники (Node.js 18.20+)
  inspect --project PATH
  init --output PATH --system NAME [--baseline VERSION]
  nav --package PATH [--write]
  validate --package PATH [--strict]
  impact --package PATH [--source-root PATH]

inspect и impact читают данные и выводят JSON. nav без --write показывает список изменений.
init создаёт только структуру в пустой папке; он не проводит анализ системы.
validate проверяет структуру и ссылки; Mermaid-синтаксис и предметный смысл проверяются отдельно.`;
try{
 const [command,...rest]=process.argv.slice(2);if(!command||command==='help'||command==='--help'){console.log(help);}else{
  const a=args(rest,['write','strict']);if(a._.length)throw new Error('Неожиданные аргументы: '+a._.join(' '));let result;
  switch(command){
   case 'inspect':only(a,['project']);if(!a.project)throw new Error('Укажите --project');result=inspect(a.project);break;
   case 'init':only(a,['output','system','baseline']);if(!a.output)throw new Error('Укажите --output');result=init(a.output,a.system,a.baseline);break;
   case 'nav':only(a,['package','write']);if(!a.package)throw new Error('Укажите --package');result=navigate(a.package,{write:!!a.write});break;
   case 'validate':only(a,['package','strict']);if(!a.package)throw new Error('Укажите --package');result=validate(a.package,{strict:!!a.strict});if(!result.ok)process.exitCode=1;break;
   case 'impact':only(a,['package','source-root']);if(!a.package)throw new Error('Укажите --package');result=impact(a.package,{sourceRoot:a['source-root']});break;
   default:throw new Error('Неизвестная команда: '+command);
  }
  console.log(JSON.stringify(result,null,2));
 }
}catch(e){console.error(JSON.stringify({ok:false,error:e.message}));process.exitCode=1;}

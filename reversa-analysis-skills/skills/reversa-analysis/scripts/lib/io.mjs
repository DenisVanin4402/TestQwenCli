import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

export const sha256=data=>crypto.createHash('sha256').update(data).digest('hex');
export const json=file=>JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
export const within=(root,file)=>{const r=path.relative(root,file);return !path.isAbsolute(r)&&r!=='..'&&!r.startsWith('..'+path.sep);};
export function rootAt(input){return fs.realpathSync(path.resolve(input));}
export function safePath(root,relative){
 if(typeof relative!=='string'||!relative||path.isAbsolute(relative)||/^[a-zA-Z]:|\\|\0/.test(relative))throw new Error('Нужен относительный путь: '+relative);
 const dest=path.resolve(root,relative);
 if(!within(root,dest))throw new Error('Путь выходит за корень: '+relative);
 let ancestor=dest;
 while(!fs.existsSync(ancestor)){
  try{if(fs.lstatSync(ancestor).isSymbolicLink())throw new Error('Недоступная символическая ссылка: '+ancestor);}catch(e){if(e.code!=='ENOENT')throw e;}
  const parent=path.dirname(ancestor);if(parent===ancestor)throw new Error('Недоступный корень');ancestor=parent;
 }
 if(!within(root,fs.realpathSync(ancestor)))throw new Error('Символическая ссылка выходит за корень: '+relative);
 return dest;
}
export function write(root,name,body){const file=safePath(root,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,body);}
export const writeJson=(root,name,value)=>write(root,name,JSON.stringify(value,null,2)+'\n');
export function walk(root,base=''){
 const files=[];
 for(const entry of fs.readdirSync(safePath(root,base||'.'),{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name,'en'))){
  const rel=base?base+'/'+entry.name:entry.name;
  if(entry.isSymbolicLink())throw new Error('Не поддерживается ссылка в распространяемом дереве: '+rel);
  if(entry.isDirectory())files.push(...walk(root,rel));else if(entry.isFile())files.push(rel);
 }
 return files;
}
export function args(argv,booleans=[]){
 const out={_:[]};
 for(let i=0;i<argv.length;i++){
  if(!argv[i].startsWith('--')){out._.push(argv[i]);continue;}
  const key=argv[i].slice(2);if(!/^[a-z][a-z-]*$/.test(key)||Object.hasOwn(out,key))throw new Error('Некорректный или повторный параметр: '+key);
  if(booleans.includes(key))out[key]=true;
  else{if(!argv[i+1]||argv[i+1].startsWith('--'))throw new Error('Нет значения --'+key);out[key]=argv[++i];}
 }
 return out;
}
export function only(options,names){for(const key of Object.keys(options))if(key!=='_'&&!names.includes(key))throw new Error('Неизвестный параметр --'+key);}

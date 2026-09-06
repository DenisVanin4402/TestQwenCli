export function prose(text){
 let marker=null;
 return text.split(/\r?\n/).map(line=>{
  const fence=line.match(/^\s*(`{3,}|~{3,})(.*)$/);
  if(fence){if(!marker)marker={char:fence[1][0],length:fence[1].length};else if(marker.char===fence[1][0]&&fence[1].length>=marker.length&&!fence[2].trim())marker=null;return '';}
  return marker?'':line.replace(/`[^`]*`/g,'');
 }).join('\n');
}
export const slug=text=>text.toLowerCase().replace(/<[^>]+>/g,'').replace(/[^\p{L}\p{N}\s_-]/gu,'').trim().replace(/\s/g,'-');
export function anchors(text){
 const body=prose(text);const ids=new Set([...body.matchAll(/\b(?:id|name)=["']([^"']+)["']/g)].map(m=>m[1]));
 const used=new Map();for(const m of body.matchAll(/^#{1,6}\s+(.+)$/gm)){const base=slug(m[1]),n=used.get(base)||0;ids.add(base+(n?'-'+n:''));used.set(base,n+1);}return ids;
}
export function links(text){
 const body=prose(text),out=[];const definitions=new Map();
 for(const m of body.matchAll(/^\s{0,3}\[([^\]]+)\]:\s*(?:<([^>]+)>|(\S+))/gm))definitions.set(m[1].toLowerCase(),m[2]||m[3]);
 const withoutDefs=body.replace(/^\s{0,3}\[[^\]]+\]:.*$/gm,'');
 const re=/\[([^\]\n]+)\](?:\(\s*(?:<([^>\n]+)>|((?:[^\s()\n]|\([^()\n]*\))+))(?:\s+["'][^\n]*?["'])?\s*\)|\[([^\]\n]*)\])/g;
 for(const m of withoutDefs.matchAll(re))out.push(m[2]||m[3]||definitions.get((m[4]||m[1]).toLowerCase())||'MISSING_REFERENCE:'+m[4]);
 // Shortcut references are checked when a matching definition exists.
 const stripped=withoutDefs.replace(re,'');for(const m of stripped.matchAll(/\[([^\]\n]+)\]/g))if(definitions.has(m[1].toLowerCase()))out.push(definitions.get(m[1].toLowerCase()));
 return out;
}
export function diagrams(text){return [...text.matchAll(/^```mermaid[^\n]*\n([\s\S]*?)^```\s*$/gm)].map(m=>m[1]);}
export function withoutGenerated(text){return text.replace(/<!-- rsa:(?:nav|children):start -->[\s\S]*?<!-- rsa:(?:nav|children):end -->\n*/g,'').replace(/<a id="rsa-[^"]+"><\/a>\n/g,'');}

function capture(raw, now = new Date()){
    const day=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
    const plus=n=>new Date(now.getFullYear(),now.getMonth(),now.getDate()+n);
    const days=['monday','tuesday','wednesday','thursday','friday','saturday','sunday'];
    const months='january february march april may june july august september october november december'.split(' ');
    const t={date:'',time:'',priority:'',tags:[],repeat:'',interval:1,mask:0,description:'',subs:[],done:false};
    const literal=[];
    const protect=value=>{literal.push(value);return '\uE000'+(literal.length-1)+'\uE001';};
    const expand=value=>value.replace(/\uE000(\d+)\uE001/g,(token,i)=>literal[i]??token);
    let s=raw.replace(/(```[\s\S]*?```|`[^`\n]*`)/g,protect);
    s=s.replace(/(^|\s)"((?:\\.|[^"\\])*)"/g,(all,space,v,offset,source)=>{
        const tail=source.slice(offset+all.length).trim();
        if(tail && !/^(?:\[|!|#|- \[|")/.test(tail))return protect(all);
        t.description+=(t.description?'\n':'')+expand(v.replace(/\\(["\\])/g,'$1'));return ' ';
    });
    const parts=s.split(/(?:-\s*)?\[\s?\]\s*/);
    s=parts.shift();t.subs=parts.map(text=>({text:expand(text.trim()),done:false})).filter(x=>x.text);
    s=s.replace(/(?:^|\s)#([\w-]+)/g,(_,v)=>{t.tags.push(v);return ' ';})
       .replace(/(?:^|\s)(!{1,3})(?=\s|$)/g,(_,v)=>{t.priority=['','Low','High','Urgent'][v.length];return ' ';});
    const take=(re,fn)=>{s=s.replace(re,(whole,...args)=>fn(...args)===false?whole:' ');};
    take(/\b(?:every (\d+) days?|every day|daily|every week|weekly|monthly|every month)\b/i,(n,off,str)=>{
        const match=str.slice(off).split(/\s/)[0];
        const phrase=str.slice(off).toLowerCase();
        t.repeat=/^(monthly|every month)/.test(phrase)?'months':/^(weekly|every week)/.test(phrase)?'weeks':'days';
        t.interval=Math.min(3650,Math.max(1,Number(n)||1));t.date=day(now);t.mask=1<<((now.getDay()+6)%7);
    });
    const dpat=days.join('|');
    take(new RegExp('\\bevery ((?:(?:'+dpat+')(?:[, ]+(?:and )?)?)+)','i'),v=>{
        t.repeat='weeks';for(const [i,name] of days.entries())if(v.toLowerCase().includes(name))t.mask|=1<<i;
        for(let i=0;i<7;i++)if(t.mask & 1<<((plus(i).getDay()+6)%7)){t.date=day(plus(i));break;}
    });
    take(/\bin (\d+) (hours?|minutes?|days?)\b/i,(n,u)=>{if(/^day/i.test(u)){t.date=day(plus(+n));return;}const d=new Date(+now+n*(/^hour/i.test(u)?3600000:60000));t.date=day(d);t.time=d.toTimeString().slice(0,5);});
    take(/\b(?:on |by |before )?(day after tomorrow|tomorrow|today)\b(?!\s+[A-Za-z]+(?:\s|$))/i,v=>{t.date=day(plus(({today:0,tomorrow:1,'day after tomorrow':2})[v.toLowerCase()]));});
    take(/\b(day after tomorrow|tomorrow|today)(?=\s+(?:by|at|before|on|remind)\b)/i,v=>{t.date=day(plus(v.toLowerCase()==='today'?0:v.toLowerCase()==='tomorrow'?1:2));});
    take(new RegExp('\\b(?:next |on |by )?('+dpat+')\\b','i'),v=>{t.date=day(plus((days.indexOf(v.toLowerCase())-(now.getDay()+6)%7+7)%7||7));});
    function setDate(d,m,y){const year=+y||now.getFullYear();let dt=new Date(year,m-1,+d);if(dt.getDate()!==+d||dt.getMonth()!==m-1)return false;if(!y&&day(dt)<day(now))dt.setFullYear(year+1);t.date=day(dt);}
    take(/\b(?:on |before |by )?(\d{1,2})\/(\d{1,2})(?:\/(\d{4}))?\b/i,(d,m,y)=>setDate(d,+m,y));
    take(new RegExp('\\b(?:on |before |by )?(\\d{1,2}) ('+months.join('|')+')(?: (\\d{4}))?\\b','i'),(d,m,y)=>setDate(d,months.indexOf(m.toLowerCase())+1,y));
    function clock(h,m,amp){h=+h;m=+m||0;if(m>59||h>(amp?12:23)||(amp&&h<1))return '';if(amp)h=h%12+(/pm|afternoon|evening/i.test(amp)?12:0);return [h,m].map(n=>String(n).padStart(2,'0')).join(':');}
    take(/\bremind(?: me)? at (\d{1,2})(?:[: ](\d{2}))?\s*(am|pm)?\b/i,(h,m,a)=>{t.reminder=clock(h,m,a);return !!t.reminder;});
    take(/\b(?:by |at |before )?(\d{1,2})(?:(?::| )(\d{2})\s*(am|pm)?|\s*(am|pm|in the morning|in the evening|in the afternoon))\b/i,(h,m,a,b)=>{t.time=clock(h,m,a||b);return !!t.time;});
    if(!t.date&&(t.time||t.reminder))t.date=day(plus((t.time||t.reminder)<=now.toTimeString().slice(0,5)?1:0));
    t.title=expand(s.replace(/\s+/g,' ').trim());return t;
}
if(typeof module!=='undefined')module.exports=capture;

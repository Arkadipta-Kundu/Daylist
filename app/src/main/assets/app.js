'use strict';
const $=id=>document.getElementById(id);
const views=['Inbox','Today','Upcoming','Done'], weekdays=['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
let view='Today', editing=null;
function button(label,action){const b=document.createElement('button');b.type='button';b.textContent=label;b.onclick=action;return b;}
function localDay(d=new Date()){return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');}
function shift(day,n){let d=new Date(day+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);}
function weekday(day){return (new Date(day+'T12:00:00Z').getUTCDay()+6)%7;}
function after(t,day){
    const floor=shift(day>=t.date?day:t.date,1);
    if(t.repeat==='months'){
        let d=new Date(floor+'T12:00:00Z');const wanted=t.monthDay||Number(t.date.slice(-2));
        const setMonth=()=>{let end=new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0));d.setUTCDate(Math.min(wanted,end.getUTCDate()));};
        setMonth();if(d.toISOString().slice(0,10)<floor){d.setUTCDate(1);d.setUTCMonth(d.getUTCMonth()+1);setMonth();}return d.toISOString().slice(0,10);
    }
    if(t.repeat==='weeks'){
        for(let i=0;i<7;i++){let d=shift(floor,i);if(t.mask & (1<<weekday(d)))return d;}
    }
    const distance=(Date.parse(floor)-Date.parse(t.date))/86400000;
    return shift(t.date,Math.ceil(distance/t.interval)*t.interval);
}
function group(t){return t.done?'Done':!t.date?'Inbox':t.date<=localDay()?'Today':'Upcoming';}
function persist(t,remove=false){
    if(Phone.write(JSON.stringify(t),remove))return true;
    $('msg').textContent='Could not save. Check free space and try again.';return false;
}
function finish(t){
    if(t.repeat&&!t.done){if(t.repeat==="months")t.monthDay=t.monthDay||Number(t.date.slice(-2));t.date=after(t,localDay());t.subs.forEach(s=>s.done=false);delete t.alerted;}
    else t.done=!t.done;
    if(persist(t))render();
}
function render(){
    const list=JSON.parse(Phone.list());
    $('view').textContent=view;$('tabs').replaceChildren();$('list').replaceChildren();
    for(const name of views){const b=button(name,()=>{view=name;render();});if(name===view)b.className='active';$('tabs').append(b);}
    const matches=list.filter(t=>group(t)===view).sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time)||a.title.localeCompare(b.title));
    if(!matches.length)$('list').textContent={Inbox:'Your undated tasks will appear here.',Today:'Nothing due today.',Upcoming:'No future tasks scheduled.',Done:'No completed tasks yet.'}[view];
    $('welcome').hidden=list.length>0;for(const t of matches){
        const row=document.createElement('div');row.className='row';
        const check=button(t.done?'✓':'○',()=>finish(t));check.setAttribute('aria-label',t.done?'Reopen '+t.title:'Complete '+t.title);
        const detail=button(t.title,()=>edit(t));detail.className='detail';
        const meta=document.createElement('small');
        meta.textContent=[t.priority,(t.tags||[]).map(v=>'#'+v).join(' '),t.repeat==='months'?'Monthly':'',t.date && (t.date<localDay()&&!t.done?'Overdue · ':'')+t.date,t.time,t.repeat==='days'?'Every '+t.interval+' day(s)':t.repeat==='weeks'?weekdays.filter((_,i)=>t.mask&(1<<i)).join(', '):'',t.subs.length?t.subs.filter(s=>s.done).length+'/'+t.subs.length+' subtasks':''].filter(Boolean).join(' · ');
        detail.append(meta);row.append(check,detail);$('list').append(row);
        if(t.subs.length){const details=document.createElement('details'),summary=document.createElement('summary');summary.textContent='Checklist';details.append(summary);
        for(const item of t.subs){const label=document.createElement('label'),box=document.createElement('input');box.type='checkbox';box.checked=item.done;box.onchange=()=>{item.done=box.checked;if(persist(t))render();};label.append(box,' '+item.text);details.append(label);}$('list').append(details);}
    }
}
function sub(s={text:'',done:false}){
    const row=document.createElement('div');row.className='sub';
    const tick=document.createElement('input');tick.type='checkbox';tick.checked=s.done;tick.setAttribute('aria-label','Subtask complete');
    const name=document.createElement('input');name.type='text';name.value=s.text;name.placeholder='Subtask';name.setAttribute('aria-label','Subtask name');
    row.append(tick,name,button('Remove',()=>row.remove()));$('subs').append(row);return name;
}
function repeatUI(){$('gap').hidden=$('repeat').value!=='days';$('week').hidden=$('repeat').value!=='weeks';}
function edit(t){
    editing=t||{id:Date.now().toString(36)+Math.random().toString(36).slice(2),title:'',description:'',date:view==='Today'?localDay():view==='Upcoming'?shift(localDay(),1):'',time:'',repeat:'',interval:1,mask:0,subs:[],done:false};
    $('msg').textContent='';$('home').hidden=true;$('editor').hidden=false;$('health').hidden=true;
    for(const [id,key] of Object.entries({title:'title',desc:'description',date:'date',time:'time',repeat:'repeat',interval:'interval'}))$(id).value=editing[key];
    $('subs').replaceChildren();editing.subs.forEach(sub);$('del').hidden=!t;$('week').replaceChildren();
    weekdays.forEach((w,i)=>{const label=document.createElement('label');const box=document.createElement('input');box.type='checkbox';box.checked=!!(editing.mask&(1<<i));label.append(box,w);$('week').append(label);});
    $('priority').value=editing.priority||'';$('tags').value=(editing.tags||[]).join(', ');$('reminder').value=editing.reminder||'';$('preview').hidden=true;repeatUI();window.scrollTo(0,0);
}
function closeEditor(){editing=null;$('editor').hidden=true;$('home').hidden=false;$('msg').textContent='';render();}
function back(){if(!$('health').hidden){$('health').hidden=true;return true;}if(editing){closeEditor();return true;}return false;}
$('close').onclick=closeEditor;$('add').onclick=()=>edit(null);$('addsub').onclick=()=>sub().focus();$('repeat').onchange=repeatUI;
$('editor').onsubmit=e=>{
    e.preventDefault();
    const t={...editing,priority:$('priority').value,tags:$('tags').value.split(/[, ]+/).filter(Boolean),reminder:$('reminder').value,title:$('title').value.trim(),description:$('desc').value,date:$('date').value,time:$('time').value,repeat:$('repeat').value,interval:Number($('interval').value)||1,
        mask:[...$('week').querySelectorAll('input')].reduce((m,b,i)=>m|(b.checked?1<<i:0),0),
        subs:[...$('subs').children].map(r=>({text:r.children[1].value.trim(),done:r.children[0].checked})).filter(s=>s.text)};
    if(!t.title||(!t.date&&(t.time||t.repeat||t.reminder))||(t.repeat==='weeks'&&!t.mask)){
        $('msg').textContent=!t.title?'Enter a task name.':!t.date?'Choose a date for this schedule.':'Select at least one weekday.';return;
    }
    if(t.repeat==='months' && (t.date!==editing.date || !t.monthDay))t.monthDay=Number(t.date.slice(-2));
    if(t.repeat==='weeks' && !(t.mask&(1<<weekday(t.date))))t.date=after(t,t.date);
    if(persist(t)){view=group(t);Phone.saveDraft('');$('quick').value='';$('composer').hidden=true;closeEditor();}
};
$('del').onclick=()=>{if(confirm('Delete this task and its subtasks?')&&persist(editing,true))closeEditor();};
$('status').onclick=()=>{$('health').hidden=!$('health').hidden;};
$('notify').onclick=()=>Phone.settings(false);$('alarms').onclick=()=>{if(!Phone.exact())Phone.settings(true);};
function refresh(){
    $('cloud').textContent=Phone.cloudStatus();for(const key of ['notifyAtDue','enter'])$(key).checked=Phone.pref(key);
    const allowed=Phone.alerts(), exact=Phone.exact();
    $('status').textContent=allowed&&exact?'Settings':'Settings · reminders';
    $('notify').textContent=allowed?'Notifications enabled':'Enable notifications';
    $('alarms').textContent=exact?'Exact alarms enabled':'Allow exact alarms';
    $('alarms').disabled=exact;
    const id=Phone.selection();const target=id&&JSON.parse(Phone.list()).find(t=>t.id===id);
    if(target)edit(target);else if(!editing)render();
}
refresh();
setInterval(()=>{if(!editing)render();},60000);

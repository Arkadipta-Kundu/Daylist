let undoTimer;
function draft(){return {...capture($('quick').value),id:Date.now().toString(36)+Math.random().toString(36).slice(2)};}
function details(focus){
    const task=draft();edit(task);$('del').hidden=true;
    if(focus)$(focus).focus();
}
function previewDraft(){
    const t=draft();$('chips').replaceChildren();
    for(const [field,label] of [['date',t.date],['time',t.time],['reminder',t.reminder],['priority',t.priority],['repeat',t.repeat],['tags',t.tags.join(' · ')],['desc',t.description?'Description':''],['addsub',t.subs.length?t.subs.length+' subtasks':'']]){
        if(label)$('chips').append(button(label,()=>details(field)));
    }
}
$('add').onclick=()=>{$('composer').hidden=false;$('quick').focus();$('composer').scrollIntoView({block:'nearest'});};
$('quick').value=Phone.draftText();if($('quick').value){$('composer').hidden=false;previewDraft();}
$('quick').oninput=()=>{Phone.saveDraft($('quick').value);previewDraft();};$('attach').onclick=()=>details('date');
$('send').onclick=()=>{
    const t=draft();if(!t.title){$('quick').focus();$('msg').textContent='Give the task a name.';return;}
    if(!persist(t))return;
    view=group(t);render();$('quick').value='';Phone.saveDraft('');previewDraft();$('msg').textContent='';
    clearTimeout(undoTimer);$('feedback').textContent='Task added  ';
    $('feedback').append(button('Undo',()=>{if(persist(t,true)){render();$('feedback').textContent='';}}));
    undoTimer=setTimeout(()=>{$('feedback').textContent='';},6500);$('quick').focus();
};
$('quick').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing&&(e.ctrlKey||Phone.pref('enter'))){e.preventDefault();$('send').click();}};
$('previewBtn').onclick=()=>{$('preview').innerHTML=Phone.markdown($('desc').value);$('preview').hidden=!$('preview').hidden;};
for(const [op,label] of Object.entries({connect:'Connect Drive',now:'Back up now',restore:'Restore from Drive',off:'Disable cloud backup',export:'Export file',import:'Import file'})){
    $('backupTools').append(button(label,()=>Phone.backup(op)));
}
$('welcome').onclick=()=>{$('health').hidden=false;$('cloud').scrollIntoView();};
for(const key of ['notifyAtDue','enter'])$(key).onchange=()=>Phone.setting(key,$(key).checked);

const assert=require('node:assert/strict'),parse=require('../app/src/main/assets/parse');
const clock=new Date(2026,11,31,18,15);
const cases=[
 ['Buy milk',{date:'',title:'Buy milk'}],['Call today',{date:'2026-12-31'}],['Call tomorrow',{date:'2027-01-01'}],
 ['Read the book Tomorrow Never Dies',{date:'',title:'Read the book Tomorrow Never Dies'}],
 ['Submit tomorrow by 2:30 pm !! #career "Focus on **Java**" () Read () Review',{date:'2027-01-01',time:'14:30',priority:'High',tags:['career'],description:'Focus on **Java**',subs:[{text:'Read',done:false},{text:'Review',done:false}]}],
 ['Call at 2 30 pm',{date:'2027-01-01',time:'14:30'}],['Meet at 14:30',{time:'14:30'}],
 ['Read next Friday',{date:'2027-01-01'}],['Test by 29/02/2028',{date:'2028-02-29'}],['Test by 31/02/2028',{date:''}],
 ['Workout every Monday Wednesday Friday at 7 am',{repeat:'weeks',mask:21,time:'07:00'}],['Workout every 2 days',{repeat:'days',interval:2}],
 ['Review in 2 hours',{time:'20:15'}],['Pay rent on 5 December',{date:'2027-12-05'}],['Finish today by 8 pm remind me at 7 pm',{time:'20:00',reminder:'19:00'}],
 ['Nice!',{priority:'',title:'Nice!'}],['Practice guitar tomorrow at 6 pm !! "Practice *barre chord transitions* for 30 minutes" () Warm up () Practice F to G () Play one song',{title:'Practice guitar',priority:'High',time:'18:00',date:'2027-01-01'}]
];for(const [input,expect] of cases)for(const [field,v] of Object.entries(expect))assert.deepEqual(parse(input,clock)[field],v,input+' / '+field);
console.log(cases.length+' capture scenarios passed');
assert.equal(parse('Read `tomorrow !! #work`',clock).title,'Read `tomorrow !! #work`');
assert.equal(parse('Study today "Say \\"hi\\""',clock).description,'Say "hi"');
assert.equal(parse('Read "hello" in class',clock).title,'Read "hello" in class');
const now=new Date(2026,10,22,16,30);
const extra=[
 ['Study Java remind at 7 pm',{title:'Study Java',date:'',time:'',reminder:'19:00',reminderDate:'2026-11-22'}],
 ['Study Java remind at 14:30',{reminder:'14:30',reminderDate:'2026-11-23'}],
 ['Pay bill remind at 25 December 2026 10 am',{title:'Pay bill',date:'',reminder:'10:00',reminderDate:'2026-12-25'}],
 ['Pay bill remind at 25/12/2026 10 am',{reminderDate:'2026-12-25'}],
 ['Submit assignment tomorrow by 5 pm remind at tomorrow 10 am',{title:'Submit assignment',date:'2026-11-23',time:'17:00',reminder:'10:00',reminderDate:'2026-11-23'}],
 ['Finish report tomorrow remind at today 8 pm',{date:'2026-11-23',reminderDate:'2026-11-22'}],
 ['Call remind at next Friday 8 pm',{reminderDate:'2026-11-27'}],
 ['Take medicine remind at in 30 minutes',{date:'',reminder:'17:00'}],
 ['Call remind in 2 hours',{reminder:'18:30'}],
 ['Practice guitar tomorrow !! remind at tomorrow 6 pm "Practice barre chords" () Warm up () Practice chord transitions',{title:'Practice guitar',priority:'High',description:'Practice barre chords',subs:[{text:'Warm up',done:false},{text:'Practice chord transitions',done:false}]}],
 ['Clean room [] Sweep floor',{title:'Clean room [] Sweep floor',subs:[]}],
 ['Inspect f() (test) `x () remind at 2 pm`',{title:'Inspect f() (test) `x () remind at 2 pm`',subs:[],reminder:''}],
 ['Review "Use () and - [ ] remind at 2 pm" () read',{description:'Use () and - [ ] remind at 2 pm',reminder:''}]
];
for(const [input,expected] of extra)for(const [key,value] of Object.entries(expected))assert.deepEqual(parse(input,now)[key],value,input);
for(const phrase of ['today','today 7 am','31/02/2027 8 pm','25:30','sometime','8 pm nonsense']){
    const input='Read remind at '+phrase,t=parse(input,now);assert.ok(t.reminderError,input);assert.equal(t.title,input);assert.equal(t.date,'');assert.equal(t.reminder,'');
}
console.log('Independent reminder, invalid expression and delimiter checks passed.');

const assert=require('node:assert/strict'),parse=require('../app/src/main/assets/parse');
const clock=new Date(2026,11,31,18,15);
const cases=[
 ['Buy milk',{date:'',title:'Buy milk'}],['Call today',{date:'2026-12-31'}],['Call tomorrow',{date:'2027-01-01'}],
 ['Read the book Tomorrow Never Dies',{date:'',title:'Read the book Tomorrow Never Dies'}],
 ['Submit tomorrow by 2:30 pm !! #career "Focus on **Java**" [] Read [] Review',{date:'2027-01-01',time:'14:30',priority:'High',tags:['career'],description:'Focus on **Java**',subs:[{text:'Read',done:false},{text:'Review',done:false}]}],
 ['Call at 2 30 pm',{date:'2027-01-01',time:'14:30'}],['Meet at 14:30',{time:'14:30'}],
 ['Read next Friday',{date:'2027-01-01'}],['Test by 29/02/2028',{date:'2028-02-29'}],['Test by 31/02/2028',{date:''}],
 ['Workout every Monday Wednesday Friday at 7 am',{repeat:'weeks',mask:21,time:'07:00'}],['Workout every 2 days',{repeat:'days',interval:2}],
 ['Review in 2 hours',{time:'20:15'}],['Pay rent on 5 December',{date:'2027-12-05'}],['Finish today by 8 pm remind me at 7 pm',{time:'20:00',reminder:'19:00'}],
 ['Nice!',{priority:'',title:'Nice!'}],['Practice guitar tomorrow at 6 pm !! "Practice *barre chord transitions* for 30 minutes" [] Warm up [] Practice F to G [] Play one song',{title:'Practice guitar',priority:'High',time:'18:00',date:'2027-01-01'}]
];for(const [input,expect] of cases)for(const [field,v] of Object.entries(expect))assert.deepEqual(parse(input,clock)[field],v,input+' / '+field);
console.log(cases.length+' capture scenarios passed');
assert.equal(parse('Read `tomorrow !! #work`',clock).title,'Read `tomorrow !! #work`');
assert.equal(parse('Study today "Say \\"hi\\""',clock).description,'Say "hi"');
assert.equal(parse('Read "hello" in class',clock).title,'Read "hello" in class');

'use strict';
const {createApp,ref,computed,nextTick}=Vue;
createApp({setup(){
 const user=ref(null),rounds=ref([]),roundId=ref(''),data=ref(null),loading=ref(false),busy=ref(false),error=ref(''),toast=ref(''),tab=ref('courses'),search=ref(''),appSearch=ref(''),choices=ref([]),adjust=ref(false),modal=ref(null),draft=ref({}),register=ref(false),modalError=ref('');let csrf='',toastTimer;
 const statusText={DRAFT:'准备中',OPEN:'报名中',CLOSED:'报名结束',ALLOCATED:'待发布',PUBLISHED:'已公示'};
 const admin=computed(()=>user.value?.role==='ADMIN');
 const tabs=computed(()=>admin.value?[{key:'courses',label:'课程管理',icon:'▦'},{key:'manage',label:'报名与分配',icon:'↗'},{key:'audit',label:'操作记录',icon:'◷'}]:[{key:'courses',label:'课程目录',icon:'▦'},{key:'application',label:'我的志愿',icon:'≡'},{key:'result',label:'分配结果',icon:'✓'}]);
 const capacity=computed(()=>data.value?.courses.reduce((n,c)=>n+c.capacity,0)||0);
 const canApply=computed(()=>data.value?.round.status==='OPEN'&&new Date(data.value.round.deadline+'+08:00')>new Date());
 const filteredCourses=computed(()=>data.value?.courses.filter(c=>[c.title,c.teacher,c.location].join(' ').toLowerCase().includes(search.value.toLowerCase()))||[]);
 const filteredApplications=computed(()=>data.value?.applications?.filter(a=>[a.username,a.display_name].join(' ').toLowerCase().includes(appSearch.value.toLowerCase()))||[]);
 const assigned=computed(()=>data.value?.courses.find(c=>c.id===data.value.application?.assigned_course));
 const resultTitle=computed(()=>data.value?.round.status==='PUBLISHED'?'你的选课结果已公布':'好机会，值得等待');
 const nextAction=computed(()=>({DRAFT:{key:'open',label:'开放报名',confirm:'开放后课程和名额将锁定，确认开放报名？'},OPEN:{key:'close',label:'关闭报名',confirm:'关闭后学生不能再修改志愿，确认结束报名？'},CLOSED:{key:'allocate',label:'运行统一分配',confirm:'请确认优先分已审核并保存。分配后不能再修改，继续？'},ALLOCATED:{key:'publish',label:'发布分配结果',confirm:'发布后所有学生可以查看自己的结果，确认发布？'}}[data.value?.round.status]));
 function notify(s){toast.value=s;clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.value='',5000);}
 async function session(){const r=await fetch('/api/session');if(!r.ok)throw Error('无法连接服务');csrf=(await r.json()).csrf;}
 async function api(path,method='GET',body){const options={method,headers:{'X-CSRF-Token':csrf}};if(body instanceof FormData)options.body=body;else if(body!==undefined){options.headers['Content-Type']='application/json';options.body=JSON.stringify(body);}const r=await fetch('/api'+path,options);let d;try{d=await r.json();}catch{throw Error('服务响应异常，请刷新重试');}if(!r.ok)throw Error(d.message||'操作失败');return d;}
 async function loadRound(){if(!roundId.value){data.value=null;return;}loading.value=true;error.value='';try{data.value=await api('/rounds/'+roundId.value);choices.value=[...(data.value.application?.preferences||[])];adjust.value=data.value.application?.allow_adjust||false;}catch(e){data.value=null;error.value=e.message;}finally{loading.value=false;}}
 async function reload(){try{rounds.value=(await api('/rounds')).rounds;if(!rounds.value.some(r=>r.id===Number(roundId.value)))roundId.value=rounds.value[0]?.id||'';await loadRound();}catch(e){error.value=e.message;}}
 async function boot(){loading.value=true;try{await session();try{user.value=(await api('/auth/me')).user;}catch{}await reload();}catch(e){error.value='服务连接失败，请确认启动窗口正在运行。';}finally{loading.value=false;}}
 async function run(action){if(busy.value)return;busy.value=true;try{await action();}catch(e){notify(e.message);}finally{busy.value=false;}}
 function courseName(id){return data.value?.courses.find(c=>c.id===id)?.title||'课程 #'+id;}
 function resultText(t){return t>=1&&t<=6?'第 '+t+' 志愿录取':t===7?'调剂录取':t===-1?'未分配':'等待分配';}
 function formatDate(s){return String(s||'').replace('T',' ').slice(0,16);}
 function choose(id){if(!user.value){loginModal(false);return;}if(choices.value.length>=6)return notify('最多选择 6 门意向课程');choices.value.push(id);notify('已加入意向清单，请到“我的志愿”提交');}
 function move(i,delta){const a=choices.value;[a[i],a[i+delta]]=[a[i+delta],a[i]];}
 function submit(){if(!user.value)return loginModal(false);return run(async()=>{await api(`/rounds/${roundId.value}/apply`,'POST',{preferences:choices.value,allow_adjust:adjust.value});await loadRound();notify('志愿已保存，截止前可继续修改');});}
 function withdraw(){if(confirm('确认撤回报名？再次报名将重新计算顺序编号。'))run(async()=>{await api(`/rounds/${roundId.value}/apply`,'DELETE');await loadRound();notify('已撤回报名');});}
 function transition(){const a=nextAction.value;if(a&&confirm(a.confirm))run(async()=>{await api(`/rounds/${roundId.value}/${a.key}`,'POST');await reload();notify('已完成：'+a.label);});}
 function saveScore(a){run(async()=>{await api(`/rounds/${roundId.value}/applications/${a.id}/score`,'POST',{priority_score:a.priority_score});await loadRound();notify('优先分已保存');});}
 function removeCourse(c){if(confirm('确认删除课程“'+c.title+'”？'))run(async()=>{await api(`/rounds/${roundId.value}/courses/${c.id}`,'DELETE');await loadRound();notify('课程已删除');});}
 function upload(e){const file=e.target.files[0];e.target.value='';if(!file)return;if(file.size>2*1024*1024)return notify('文件不能超过 2 MB');run(async()=>{const f=new FormData();f.append('file',file);const d=await api(`/rounds/${roundId.value}/courses.xlsx`,'POST',f);await loadRound();notify('已导入 '+d.count+' 门课程');});}
 function open(kind,title,body){modal.value={kind,title};draft.value={...body};modalError.value='';nextTick(()=>document.querySelector('.dialog input')?.focus());}
 function closeModal(){if(!busy.value)modal.value=null;}
 function loginModal(signup){register.value=signup;open('auth',signup?'开启你的学习计划':'欢迎回到课选',{username:'',password:'',display_name:''});}
 function passwordModal(){open('password','修改登录密码',{old_password:'',new_password:''});}
 function roundModal(r){const d=new Date(Date.now()+7*86400000);open('round',r?'编辑选课批次':'创建新的选课批次',r||{title:'',description:'每人分配一门。优先分由老师根据先修课程或实际需要审核；同分按报名编号排序。',deadline:new Date(d.getTime()+8*3600000).toISOString().slice(0,16)});}
 function courseModal(c){open('course',c?'编辑课程':'添加一门实训课程',c||{title:'',teacher:'',location:'',schedule:'',description:'',capacity:24});}
 async function saveModal(){if(busy.value)return;busy.value=true;modalError.value='';try{const kind=modal.value.kind;if(kind==='auth'){user.value=(await api('/auth/'+(register.value?'register':'login'),'POST',draft.value)).user;tab.value='courses';notify('欢迎，'+user.value.display_name);}else if(kind==='password'){await api('/auth/password','POST',draft.value);user.value=null;await session();tab.value='courses';notify('密码已修改，请重新登录');}else if(kind==='round'){if(draft.value.id)await api('/rounds/'+draft.value.id,'PUT',draft.value);else roundId.value=(await api('/rounds','POST',draft.value)).id;notify('批次已保存');}else{await api(`/rounds/${roundId.value}/courses`+(draft.value.id?'/'+draft.value.id:''),draft.value.id?'PUT':'POST',draft.value);notify('课程已保存');}modal.value=null;await reload();}catch(e){modalError.value=e.message;}finally{busy.value=false;}}
 function logout(){run(async()=>{await api('/auth/logout','POST');user.value=null;tab.value='courses';roundId.value='';await session();await reload();notify('已退出登录');});}
 document.addEventListener('keydown',e=>{if(!modal.value)return;if(e.key==='Escape')closeModal();if(e.key==='Tab'){const nodes=[...document.querySelectorAll('.dialog button,.dialog input,.dialog textarea')].filter(n=>!n.disabled);const first=nodes[0],last=nodes.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}});
 boot();return {user,rounds,roundId,data,loading,busy,error,toast,tab,search,appSearch,choices,adjust,modal,draft,register,modalError,statusText,admin,tabs,capacity,canApply,filteredCourses,filteredApplications,assigned,resultTitle,nextAction,reload,loadRound,courseName,resultText,formatDate,choose,move,submit,withdraw,transition,saveScore,removeCourse,upload,closeModal,loginModal,passwordModal,roundModal,courseModal,saveModal,logout};
}}).mount('#app');

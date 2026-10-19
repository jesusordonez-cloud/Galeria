const channels = [
  {id:"estrellas",name:"Las Estrellas",logo:"✦",color:"#17152b",region:"mexico",now:"Noticiero En Punto",next:"El Amor Invencible"},
  {id:"canal5",name:"Canal 5",logo:"5",color:"#111",region:"mexico",now:"Vecinos",next:"Como dice el dicho"},
  {id:"azteca",name:"Azteca UNO",logo:"UNO",color:"#10151d",region:"mexico",now:"Hechos Meridiano",next:"Ventaneando"},
  {id:"foro",name:"FOROtv",logo:"FORO",color:"#d30f20",region:"mexico",now:"Expreso de la Mañana",next:"FORO Global"},
  {id:"nmas",name:"N+",logo:"N+",color:"#e51e34",region:"mexico",now:"Despierta",next:"Noticieros N+"},
  {id:"telemundo",name:"Telemundo",logo:"T",color:"#10151d",region:"espanol",now:"Noticias",next:"En Casa"},
  {id:"univision",name:"Univision",logo:"U",color:"#122031",region:"espanol",now:"Despierta América",next:"Noticias"},
  {id:"espn",name:"ESPN",logo:"ESPN",color:"#141c26",region:"mundo",now:"SportsCenter",next:"Fútbol"},
  {id:"tudn",name:"TUDN",logo:"TUDN",color:"#097e58",region:"mexico",now:"Línea de 4",next:"Fútbol"},
  {id:"disney",name:"Disney",logo:"Disney",color:"#0874dc",region:"mundo",now:"Programación infantil",next:"Series"}
];

const categories = ["México","Español","Mundo","Deportes","Noticias","Entretenimiento","Cultura","Infantil","Música"];

const guideRows = [
  ["✦","Las Estrellas HD","Noticiero En Punto","El Amor Invencible","La Rosa de Guadalupe"],
  ["5","Canal 5 HD","Vecinos","Como dice el dicho","La CQ"],
  ["UNO","Azteca UNO","Hechos Meridiano","Ventaneando","Lo que callamos las mujeres"],
  ["FORO","FOROtv","Expreso de la Mañana","FORO Global","Las Noticias"],
  ["N+","N+","Despierta","Noticieros N+","En Punto"]
];

const recommended = document.getElementById("recommended");
const categoriesEl = document.getElementById("categories");
const guide = document.getElementById("guide");
const toast = document.getElementById("toast");

function channelCard(ch,i){
  const b=document.createElement("button");
  b.className="channel-card"+(i===0?" selected":"");
  b.dataset.channel=ch.id;
  b.style.background=`linear-gradient(145deg,${ch.color},#05090f)`;
  b.innerHTML=`<strong>${ch.logo}</strong><small>${ch.name}</small>`;
  b.addEventListener("click",()=>selectChannel(ch));
  return b;
}
channels.forEach((c,i)=>recommended.appendChild(channelCard(c,i)));

categories.forEach((name,i)=>{
  const b=document.createElement("button");
  b.className="category-card";
  b.innerHTML=`<b>${name}</b>`;
  b.addEventListener("click",()=>showToast(`Categoría: ${name}`));
  categoriesEl.appendChild(b);
});

guideRows.forEach(r=>{
  const row=document.createElement("div");
  row.className="guide-row";
  row.innerHTML=`<div class="guide-channel"><div class="guide-logo">${r[0]}</div><span>${r[1]}</span></div>
    <div class="guide-programs"><span>${r[2]}</span><span>${r[3]}</span><span>${r[4]}</span></div>`;
  guide.appendChild(row);
});

function selectChannel(ch){
  document.querySelectorAll(".channel-card").forEach(x=>x.classList.toggle("selected",x.dataset.channel===ch.id));
  document.getElementById("selectedLogo").textContent=ch.logo;
  document.getElementById("selectedTitle").textContent=ch.name+" HD";
  document.getElementById("currentProgram").textContent=ch.now;
  document.getElementById("nextProgram").textContent="16:00 – 17:00 · "+ch.next;
  showToast(ch.name+" seleccionado");
}
function showToast(msg){
  toast.textContent=msg; toast.classList.add("show");
  clearTimeout(window.__toast); window.__toast=setTimeout(()=>toast.classList.remove("show"),1800);
}
function tick(){
  const d=new Date();
  document.getElementById("clock").textContent=d.toLocaleTimeString("es-MX",{hour:"2-digit",minute:"2-digit",hour12:false});
  document.getElementById("date").textContent=d.toLocaleDateString("es-MX",{weekday:"short",day:"2-digit",month:"short",year:"numeric"}).replace(".","");
  const mins=d.getMinutes(); document.getElementById("progressBar").style.width=(mins/60*100)+"%";
}
tick(); setInterval(tick,30000);

document.querySelectorAll(".nav-item").forEach(n=>{
  n.addEventListener("click",()=>{
    document.querySelectorAll(".nav-item").forEach(x=>x.classList.remove("active"));
    n.classList.add("active"); showToast(n.textContent.trim());
  });
});

const focusables = () => [...document.querySelectorAll("button")].filter(x=>x.offsetParent!==null);
document.addEventListener("keydown",e=>{
  const list=focusables(); if(!list.length)return;
  let i=list.indexOf(document.activeElement); if(i<0)i=0;
  const rect=list[i].getBoundingClientRect();
  const candidates=list.map((el,idx)=>({el,idx,r:el.getBoundingClientRect()})).filter(x=>x.idx!==i);
  let best=null,score=Infinity;
  for(const c of candidates){
    let primary,secondary;
    if(e.key==="ArrowRight" && c.r.left>=rect.right-2){primary=c.r.left-rect.right;secondary=Math.abs(c.r.top-rect.top)}
    else if(e.key==="ArrowLeft" && c.r.right<=rect.left+2){primary=rect.left-c.r.right;secondary=Math.abs(c.r.top-rect.top)}
    else if(e.key==="ArrowDown" && c.r.top>=rect.bottom-2){primary=c.r.top-rect.bottom;secondary=Math.abs(c.r.left-rect.left)}
    else if(e.key==="ArrowUp" && c.r.bottom<=rect.top+2){primary=rect.top-c.r.bottom;secondary=Math.abs(c.r.left-rect.left)}
    else continue;
    const s=primary*3+secondary;
    if(s<score){score=s;best=c.el}
  }
  if(best){e.preventDefault();best.focus();best.scrollIntoView({block:"nearest",inline:"nearest"})}
});
window.addEventListener("load",()=>document.querySelector(".nav-item").focus());

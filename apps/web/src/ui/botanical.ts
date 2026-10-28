export type BotanicalAsset={species:string;file:string;credit:string;license:string;url:string;kind:'photo'|'plate';width:number;height:number}
export const botanicalAssets:BotanicalAsset[]=[
{species:'Ginkgo biloba',file:'/botanical/ginkgo-biloba-hero.webp',credit:'Dan Keck',license:'CC0',url:'https://commons.wikimedia.org/wiki/File:Ginkgo_biloba_(35096003376).jpg',kind:'photo',width:1280,height:720},
{species:'Jatropha curcas',file:'/botanical/jatropha-curcas-plate.webp',credit:'F. M. Blanco · 1880–1883',license:'公有领域',url:'https://commons.wikimedia.org/wiki/File:Jatropha_curcas_Blanco2.384-cropped.jpg',kind:'plate',width:1280,height:1841},
{species:'Camellia oleifera',file:'/botanical/camellia-oleifera.webp',credit:'USDA ARS',license:'公有领域',url:'https://commons.wikimedia.org/wiki/File:Camellia_oleifera.jpg',kind:'photo',width:1059,height:1343}]
export function botanicalAsset(name?:string|null){if(!name||/['"“”‘’]/.test(name))return undefined;const words=name.trim().split(/\s+/);if(words.slice(2).some(w=>['var.','f.','subsp.','ssp.','×'].includes(w)))return undefined;return botanicalAssets.find(a=>a.species===words.slice(0,2).join(' '))}

export const sourceProfiles=[
 {id:'wfo',name:'World Flora Online',type:'分类骨架',url:'https://www.worldfloraonline.org/',description:'以接受学名、异名和分类关系为线索，建立来源名称之间可解释的连接。',usage:'项目使用目标名称与关系切片，不等同全球名录全量入库。',boundary:'名称匹配与物种身份核验分别记录。',tokens:['wfo_']},
 {id:'nhm',name:'NHM · 植物性状资料',type:'外观与性状',url:'https://data.nhm.ac.uk/',description:'保留叶形、叶缘、花色、生长型等原始性状词，形成可查询的机器提取候选。',usage:'Combined Traits 与分类映射分别处理，原始值与规范化投影同时保留。',boundary:'词面规范化不等于人工鉴定；无法解释的特征不强制归类。',tokens:['combined.zip','nhm_taxon_']},
 {id:'foc',name:'Flora of China',type:'植物志来源',url:'http://www.efloras.org/',description:'利用实际保存的植物志性状资料，为植物外观查询提供可回溯的来源线索。',usage:'经 NHM 保存的 Flora of China / eFloras 资料进入形态管线。',boundary:'可能与 Combined 数据重叠，记录量不能相加为独立物种数。',tokens:['flora_of_china']},
 {id:'gbif',name:'GBIF',type:'名称匹配',url:'https://www.gbif.org/',description:'保留分类名称匹配响应与候选关系，帮助追踪名称核对过程。',usage:'使用已保存的匹配资料，不宣称导入全球物种分布数据。',boundary:'匹配结果不自动确认物种，也不补造地理分布。',tokens:['gbif','match_candidates_20261011']},
 {id:'sp2000',name:'中国生物物种名录 · 2022',type:'可靠中文名',url:'https://www.sp2000.org.cn/',description:'以接受种学名精确匹配中文名称，保留工作簿行号、来源版本与文件摘要。',usage:'实际使用用户提供的2022版结构化名录，非2026版；既有可靠名称历史保留。',boundary:'不把属名当种名，不翻译拉丁名，不确定名称继续留空。',tokens:['sp2000','col2022','中国生物物种名录']},
 {id:'iflora',name:'iFlora 植物数据接口',type:'名称补充',url:'https://api.iflora.cn/',description:'为中文名补充提供独立来源断言，与课程标题和名录匹配结果保留各自证据。',usage:'使用实际取得的名称元数据；上游未声明的版本不自行补写。',boundary:'未使用的数据源不会被标记为已采集，名称重复不计为新增物种。',tokens:['iflora']},
 {id:'usda',name:'USDA FoodData Central',type:'食品营养',url:'https://fdc.nal.usda.gov/',description:'保留食品脂肪酸含量与原始营养计量口径，作为独立类别供浏览与追溯。',usage:'食品营养数据与含油率、油脂脂肪酸组成、FAME组成分别建模。',boundary:'每100g食品的营养量不能直接当作种子油脂肪酸百分比。',tokens:['fooddata','usda']},
 {id:'course',name:'课程资料与脂肪酸文献',type:'研究样本',url:null,description:'课程PDF、课程提取资料及实际保存的研究论文连接独立样本、观测值与证据定位。',usage:'保留页码、表号、行列、原数值和单位；文献登记不代表全文已解析。',boundary:'样本身份不确定时保持未关联，缺失值不填0，不转载许可不明的全文。',tokens:['课程','.pdf','oil_samples','plants.json','fatty_acid']},
 {id:'media',name:'iNaturalist · 授权植物影像',type:'媒体与署名',url:'https://www.inaturalist.org/',description:'按明确物种身份和许可筛选照片，保留作者、原观察链接和本地持久化衍生图。',usage:'动态照片采用CC0 / CC BY白名单；静态图鉴素材另列署名与许可。',boundary:'图像用于图鉴展示，不作为平台鉴定证据；无图不以其他物种替代。',tokens:['inaturalist']},
] as const
export function sourceProfile(dataset:string){const q=dataset.toLowerCase();return sourceProfiles.find(p=>p.tokens.some(t=>q.includes(t.toLowerCase())))??null}

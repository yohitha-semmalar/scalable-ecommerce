import type {Category,Product,SystemMetrics} from '../types';
export const categories:Category[]=[{slug:'electronics',name:'Electronics',description:'Thoughtful technology. Less friction, more possibility.'},{slug:'fashion',name:'Fashion',description:'Easy silhouettes and dependable everyday layers.'},{slug:'home',name:'Home & living',description:'Make room for the objects you love.'},{slug:'accessories',name:'Accessories',description:'The small details that go a long way.'},{slug:'books',name:'Books',description:'New perspectives, one page at a time.'},{slug:'lifestyle',name:'Lifestyle',description:'Good company for your daily rituals.'}];
// Product-specific photographs; locally rendered fallback never substitutes another product.
const photo=(id:string)=>`https://images.unsplash.com/${id}?auto=format&fit=crop&w=900&q=80`;
const rows:[string,string,number,string,string][]=[
['Studio wireless headphones','electronics',4990,'photo-1546435770-a3e426bf472b','Immersive sound, without the noise. Soft over-ear cushions and a balanced, detailed listening experience.'],
['Everyday canvas tote','accessories',890,'photo-1553062407-98eeb64c6a62','A durable carryall with room for your everyday essentials.'],
['Arc reading lamp','home',2490,'photo-1507473885765-e6ed057f782c','Warm, focused light for your bedside or reading corner.'],
['The art of slow living','books',650,'photo-1544947950-fa07a98d237f','An illustrated invitation to make space for the everyday.'],
['Classic low-top sneakers','fashion',2790,'photo-1549298916-b41d501d3772','Comfortable daily sneakers with a flexible sole.'],
['Ceramic morning mug','home',590,'photo-1514228742587-6b1558fcca3d','A glazed ceramic cup for a better morning ritual.'],
['Pocket digital camera','electronics',12490,'photo-1516035069371-29a1b244cc32','A compact camera for everyday observations.'],
['Linen everyday shirt','fashion',1890,'photo-1598033129183-c4f50c736f10','A relaxed lightweight shirt with a breathable weave.'],
['Analog field watch','accessories',3290,'photo-1524805444758-089113d48a6d','A clear dial and comfortable strap for everyday wear.'],
['Quiet spaces','books',1190,'photo-1495446815901-a7297e633e8d','A collection of inspiring interiors and thoughtful spaces.'],
['Daily hydration bottle','lifestyle',990,'photo-1602143407151-7111542de6e8','Reusable insulated bottle for your commute and beyond.'],
['Compact desk speaker','electronics',3490,'photo-1608043152269-423dbba4e7e1','Room-filling audio in a small, considered package.'],
['Woven accent cushion','home',1190,'photo-1584100936595-c0654b55a2e2','Tactile woven texture for your favourite corner.'],
['Weekend backpack','accessories',2190,'photo-1622260614153-03223fb72052','A practical daypack with generous pockets.'],
['Essential cotton tee','fashion',790,'photo-1521572163474-6864f9cf17ab','A soft, midweight cotton wardrobe staple.'],
['The creative notebook','books',450,'photo-1531346878377-a5be20888e57','An open invitation to sketch, write and think.'],
['Everyday sunglasses','accessories',1490,'photo-1511499767150-a48a237f0083','A classic silhouette with tinted lenses.'],
['Movement yoga mat','lifestyle',1790,'photo-1601925260368-ae2f83cf8b7f','A cushioned mat for stretching and a daily reset.'],
['Minimal desk clock','home',1390,'photo-1563861826100-9cb868fdbe1c','A simple face that makes time easy to read.'],
['Mechanical keyboard','electronics',3990,'photo-1587829741301-dc798b83add3','Tactile keys for a satisfying daily workspace.'],
['Weekend denim jacket','fashion',3490,'photo-1543076447-215ad9ba6923','A versatile layer in durable cotton denim.'],
['Green living guide','books',890,'photo-1512820790803-83ca734da794','Practical ideas for a more considered home.'],
['Trail walking shoes','lifestyle',2990,'photo-1542291026-7eec264c27ff','Lightweight footwear for your daily movement.'],
['Portable travel flask','lifestyle',1290,'photo-1523362628745-0c100150b504','Keep your favourite drink close on the move.']];
export const products:Product[]=rows.map(([name,category,price,img,description],i)=>({id:`prd-${String(i+1).padStart(3,'0')}`,name,category,price,originalPrice:i%4===0?Math.round(price*1.25):undefined,description,images:[photo(img)],stock:i===20?0:8+i%13,specifications:{Collection:'Everyday essentials',Care:category==='electronics'?'Wipe with a dry cloth':'Follow included care instructions',Warranty:category==='electronics'?'12-month demo warranty':'Demo return policy applies'},createdAt:new Date(Date.UTC(2026,9,19-i)).toISOString(),featured:i<8}));
export const benchmarks:SystemMetrics={source:'Illustrative dataset — not a Locust measurement',simulated:true,results:[{scenario:'Low',users:10,throughput:48,average:42,median:38,p95:74,p99:102,errorRate:0},{scenario:'Medium',users:100,throughput:380,average:88,median:72,p95:160,p99:240,errorRate:.2},{scenario:'High',users:500,throughput:1120,average:210,median:170,p95:420,p99:650,errorRate:1.8}]};

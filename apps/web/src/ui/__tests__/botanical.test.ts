import test from 'node:test'
import assert from 'node:assert/strict'
import {botanicalAsset,botanicalAssets} from '../botanical.ts'

test('illustrations match an evidenced species while keeping unillustrated taxa empty',()=>{
 assert.equal(botanicalAsset('Ginkgo biloba L.')?.species,'Ginkgo biloba')
 assert.equal(botanicalAsset('Jatropha curcas')?.kind,'plate')
 assert.equal(botanicalAsset('Camellia oleifera')?.credit,'USDA ARS')
 for(const name of [undefined,null,'','Ginkgo macrophylla','Cycas revoluta Thunb.','银杏'])assert.equal(botanicalAsset(name),undefined)
})
test('species image is never assigned to a named infraspecific taxon or cultivar',()=>{
 for(const name of ['Ginkgo biloba var. cylindrica','Ginkgo biloba f. aurea','Ginkgo biloba subsp. test','Ginkgo biloba × test',"Ginkgo biloba 'Fastigiata'"])assert.equal(botanicalAsset(name),undefined)
})

// Isolated native serializer fixtures, with vanilla resources to keep the dependency set small.
ServerEvents.recipes(event => {
  if(Platform.isLoaded('jdte')){
    event.custom({type:'jdte:infusion',id:'compat:jdte_infusion',input:{id:'minecraft:iron_ingot',count:2},fluid:{id:'minecraft:water',amount:250},output:{id:'minecraft:gold_ingot',count:2},energy:12345}).id('compat:jdte_infusion')
    event.custom({type:'jdte:bio_factory',specimen:{item:'minecraft:egg'},inputs:[{ingredient:{item:'minecraft:stone'},count:2}],outputs:[{item:{id:'minecraft:gold_ingot',count:2},chance:0.5}],process_ticks:40,energy:1234}).id('compat:jdte_bio')
  }
  if(Platform.isLoaded('touhou_little_maid'))event.custom({type:'touhou_little_maid:altar_recipe_serializers',category:'misc',entity:'minecraft:item',group:'altar_recipe',ingredients:[{item:'minecraft:iron_ingot'},{item:'minecraft:gold_ingot'}],lang:'jei.touhou_little_maid.altar_craft.item_craft.result',power:0.123,result:{id:'minecraft:emerald',count:2}}).id('compat:maid_altar')
  if(Platform.isLoaded('enderio'))event.custom({type:'enderio:alloy_smelting',energy:10000,experience:1,inputs:[{item:'minecraft:iron_ingot',count:1},{item:'minecraft:gold_ingot',count:1},{item:'minecraft:diamond',count:1}],output:{id:'minecraft:netherite_ingot',count:1}}).id('compat:enderio_generic')
  event.custom({type:'modern_industrialization:bbb_vac',eu:25,duration:200,
    item_inputs:['iron_ingot','gold_ingot','diamond','emerald','coal','redstone','quartz','copper_ingot'].map(item=>({item:'minecraft:'+item,amount:2})),
    fluid_inputs:['water','lava','water','lava'].map(fluid=>({fluid:'minecraft:'+fluid,amount:1000})),
    item_outputs:[{item:'minecraft:netherite_ingot',amount:1}]
  }).id('compat:mi_eight_four')
  event.custom({type:'forbidden_arcanus:ritual',inputs:[{ingredient:{item:'minecraft:coal'},amount:2},{ingredient:{item:'minecraft:diamond'},amount:2}],
    main_ingredient:{item:'minecraft:redstone'},result:{type:'forbidden_arcanus:create_item',result_item:{id:'minecraft:emerald',count:1}},
    essences:{aureal:100,souls:5,blood:2000,experience:10},forge_tier:1,match_tier_exact:false,
    enhancers:['forbidden_arcanus:artisan_relic'],magic_circle:'forbidden_arcanus:create_item',duration:100
  }).id('compat:ritual')
  ;[1,2,4,6,8].forEach(n=>{let materials=[];for(let i=0;i<n;i++)materials.push({item:'minecraft:gold_ingot'});event.custom({type:'ars_nouveau:enchanting_apparatus',
    pedestalItems:materials,
    reagent:{item:'minecraft:diamond'},result:{id:'minecraft:emerald',count:1},sourceCost:10,keepNbtOfReagent:false
  }).id('compat:apparatus_'+n)})
})

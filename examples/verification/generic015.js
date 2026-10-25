// Dev-only framework codec; never installed with the production mod.
ServerEvents.recipes(event => {
  event.custom({
    type: 'kubejs_editor_verification:generic',
    feedstock: {ingredient: {item: 'minecraft:iron_ingot'}, count: 20480},
    bath: {ingredient: {type: 'neoforge:single', fluid: 'minecraft:water'}, amount: 250},
    product: {id: 'minecraft:gold_ingot', count: 2},
    extra: {key: 'kubejs_editor_verification:red', quantity: 4},
    settings: {work: 40, enabled: true}
  }).id('kubejs_editor_verification:framework')
})

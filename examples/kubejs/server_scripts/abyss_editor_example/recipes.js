ServerEvents.recipes(event => {
  event.shapeless('kubejs:abyss_editor_sample_dust', ['minecraft:charcoal', 'minecraft:redstone'])
    .id('abyss_editor_example:sample_dust');
  event.custom({
    type: 'create:pressing',
    ingredients: [{ item: 'kubejs:abyss_editor_sample_dust' }],
    results: [{ id: 'minecraft:coal', count: 1 }]
  }).id('abyss_editor_example:sample_pressing');
});

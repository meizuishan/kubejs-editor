// 独立示例，不会自动加载。复制到 server_scripts 后才会执行。
ServerEvents.recipes(event => {
  event.custom({
    type: 'create:splashing',
    ingredients: [{ item: 'minecraft:gravel' }],
    results: [{ id: 'minecraft:flint', chance: 0.25 }]
  }).id('kubejs:example_fan_washing');

  if (Platform.isLoaded('create_dragons_plus')) {
    event.custom({
      type: 'create_dragons_plus:freezing',
      ingredients: [{ item: 'minecraft:magma_cream' }],
      results: [{ id: 'minecraft:slime_ball' }]
    }).id('kubejs:example_fan_freezing');
  }
});

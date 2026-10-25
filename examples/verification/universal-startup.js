// Isolated integration fixture; never install into a player instance.
let editorUniversalMI
MIMachineEvents.registerRecipeTypes(event => {
  editorUniversalMI = event.register('bbb_vac').withItemInputs().withFluidInputs().withItemOutputs()
})
MIMachineEvents.registerMachines(event => {
  const shape = event.layeredShape('steel', [['ccc', 'c#c', 'ccc']])
    .key('c', event.memberOfBlock('modern_industrialization:steel_machine_casing'), event.noHatch()).build()
  event.simpleElectricCraftingMultiBlock('bbb_vac', 'bbb_vac', editorUniversalMI, shape,
    event.progressBar(96, 42, 'arrow'),
    inputs => inputs.addSlots(8, 12, 8, 1), outputs => outputs.addSlot(134, 46),
    fluids => fluids.addSlots(8, 46, 4, 1), outputs => {},
    'steel', 'assembler', true, false, false)
})

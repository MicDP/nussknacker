import _ from 'lodash'

class ProcessUtils {

  nothingToSave = (state) => {
    const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
    const processToDisplay = state.graphReducer.processToDisplay
    return !_.isEmpty(fetchedProcessDetails) ? _.isEqual(fetchedProcessDetails.json, processToDisplay) : true
  }

  processDisplayName = (processId, versionId) => {
    return `${processId}:v${versionId}`
  }

  findAvailableVariables = (nodeId, process, processDefinition) => {
    const globalVariables = _.mapValues(processDefinition.globalVariables, (v) => {return v.value.refClazzName})
    const variablesDefinedBeforeNode = this._findVariablesDeclaredBeforeNode(nodeId, process, processDefinition);
    const variables = {
      ...globalVariables,
      ...variablesDefinedBeforeNode
    }
    return _.mapKeys(variables, (value, variableName) => {return `#${variableName}`})
  }

  _findVariablesDeclaredBeforeNode = (nodeId, process, processDefinition) => {
    const previousNodes = this._findPreviousNodes(nodeId, process, processDefinition)
    const variablesDefinedBeforeNodeList = _.flatMap(previousNodes, (nodeId) => {
      return this._findVariablesDefinedInProcess(nodeId, process, processDefinition)
    })
    return this._listOfObjectsToObject(variablesDefinedBeforeNodeList);
  }

  _listOfObjectsToObject = (list) => {
    return _.reduce(list, (memo, current) => { return {...memo, ...current}},  {})
  }

  _findVariablesDefinedInProcess = (nodeId, process, processDefinition) => {
    const node = _.find(process.nodes, (node) => node.id == nodeId)
    switch (node.type) {
      case "Source": {
        return [{"input": _.get(processDefinition, `sourceFactories[${node.ref.typ}].returnType.refClazzName`)}]
      }
      case "Enricher": {
        return [{[node.output]: _.get(processDefinition, `services[${node.service.id}].returnType.refClazzName`)}]
      }
      case "CustomNode": {
        const customNodeType = node.nodeType
        const outputVariableName = node.outputVar
        const outputClazz = _.get(processDefinition, `customStreamTransformers[${customNodeType}].returnType.refClazzName`)
        return _.isEmpty(outputClazz) ? [] : [ {[outputVariableName]: outputClazz} ]
      }
      case "VariableBuilder": {
        return [{[node.varName]: "java.lang.Object"}] //fixme co tutaj?
      }
      case "Variable": {
        return [{[node.varName]: "java.lang.Object"}] //fixme co tutaj?
      }
      default: {
        return []
      }
    }
  }

  _findPreviousNodes = (nodeId, process) => {
    const nodeEdge = _.find(process.edges, (edge) => _.isEqual(edge.to, nodeId))
    if (_.isEmpty(nodeEdge)) {
      return []
    } else {
      const previousNodes = this._findPreviousNodes(nodeEdge.from, process)
      return _.concat([nodeEdge.from], previousNodes)
    }
  }
}

export default new ProcessUtils()

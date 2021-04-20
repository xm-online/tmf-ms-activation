def event = lepContext.inArgs.sagaEvent
event.taskContext.put('test', 'data')

return lepContext.lep.proceed(lepContext.lep.getMethodArgValues())
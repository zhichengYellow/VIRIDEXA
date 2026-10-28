import * as echarts from 'echarts/core'
import {BarChart} from 'echarts/charts'
import {TitleComponent,AriaComponent,GridComponent,TooltipComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
echarts.use([BarChart,TitleComponent,AriaComponent,GridComponent,TooltipComponent,CanvasRenderer])
export {echarts}

define(function () {
	var current = {

		/**
		 * Render vCloud identifier.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:vm:vcloud:id');
		},

		/**
		 * Render VM vCloud.
		 */
		renderFeatures: function (subscription) {
			var result = '';
			if (subscription.parameters && subscription.parameters['service:vm:vcloud:url'] && subscription.parameters['service:vm:vcloud:organization']) {
				// Add vDirector link to the home page
				result += current.$super('renderServicelink')('home', subscription.parameters['service:vm:vcloud:url']
				 + '/' + subscription.parameters['service:vm:vcloud:organization']
				 + '/#/vmListPage?', null , null, ' target="_blank"');
			}
			if (subscription.parameters && subscription.parameters.console) {
				// Add Console
				result += '<button class="btn-link" data-toggle="popover" data-html="true" data-content="<img src=';
				result += '\'rest/service/vm/vcloud/' + subscription.id + '/console.png\'';
				result += '></img>"><span data-toggle="tooltip" title="' + current.$messages['service:vm:vcloud:console'];
				result += '" class="fa-stack"><i class="fa fa-square fa-stack-1x"></i><i class="fa fa-terminal fa-stack-1x fa-inverse"></i></span></button>';
			}
			return result;
		},

		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:vm:vcloud:id', 'service/vm/vcloud/');
		},

		/**
		 * Render vCloud details : id, name of VM, description, CPU, memory and vApp.
		 */
		renderDetailsKey: function (subscription, $tr) {
			var vm = subscription.data.vm;
			if (subscription.parameters && subscription.parameters['service:vm:vcloud:url'] && subscription.parameters['service:vm:vcloud:organization'] && vm.vappId) {
				// Update vDirector link to the specific vAPP
				$tr.find('a.feature').attr('href', subscription.parameters['service:vm:vcloud:url'] + '/' + subscription.parameters['service:vm:vcloud:organization'] + '/#/vmList?vapp=' + vm.vappId);
			}

			return current.$super('generateCarousel')(subscription, [
				[
					'service:vm:vcloud:id', current.renderKey(subscription)
				],
				[
					'name', vm.name
				],
				[
					'service:vm:os', vm.os
				],
				[
					'service:vm:resources', current.$super('icon')('sliders') + vm.cpu + ' CPU, ' + formatManager.formatSize((vm.ram || 0) * 1024 * 1024)
				],
				[
					'service:vm:vcloud:vapp', current.$super('icon')('server', 'service:vm:vcloud:vapp') + vm.vapp
				],
				[
					'service:vm:vcloud:organization', current.$super('icon')('building-o', 'service:vm:vcloud:organization') + subscription.parameters['service:vm:vcloud:organization']
				]
			], 1);
		}
	};
	return current;
});

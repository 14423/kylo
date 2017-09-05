define(['angular', 'admin/module-name','kylo-utils/LazyLoadUtil','codemirror-require/module','constants/AccessConstants','kylo-common', 'kylo-services','jquery'], function (angular,moduleName,lazyLoadUtil,AccessConstants) {
    var module = angular.module(moduleName, []);

    /**
     * LAZY loaded in from /app.js
     */
    module.config(['$stateProvider',function ($stateProvider) {
        $stateProvider.state('jcr-query',{
            url:'/admin/jcr-query',
            views: {
                'content': {
                    templateUrl: 'js/admin/jcr/jcr-query.html',
                    controller:"JcrQueryController",
                    controllerAs:"vm"
                }
            },
            resolve: {
                loadMyCtrl: lazyLoadController(['admin/jcr/JcrQueryController'])
            },
            data:{
                breadcrumbRoot:false,
                displayName:'JCR Admin',
                module:moduleName,
                permissions:[]
            }
        })

        function lazyLoadController(path){
            return lazyLoadUtil.lazyLoadController(path,'admin/module-require');
        }

    }]);

    module.run(['$ocLazyLoad', function ($ocLazyLoad) {
        $ocLazyLoad.load({
            name: 'kylo', files: ['bower_components/angular-ui-grid/ui-grid.css', 'assets/ui-grid-material.css'
            ], serie: true
        })
    }]);
    return module;
});



